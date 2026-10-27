package dev.soto.panelgrove.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import dev.soto.panelgrove.R
import dev.soto.panelgrove.storage.copyBounded
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** App-owned thumbnails survive provider permission changes and never replace generated covers. */
class CustomCoverStore(private val context: Context) {
    private val directory = File(context.filesDir, "custom-covers").apply { mkdirs() }
    private val staging = File(context.cacheDir, "cover-imports").apply { mkdirs() }
    fun file(key: String): File {
        require(Regex("[A-Za-z0-9_-]{1,100}").matches(key))
        return File(directory, "$key.png")
    }

    suspend fun import(uri: Uri): String {
        val key = UUID.randomUUID().toString()
        val staged = File.createTempFile("cover-", ".image", staging)
        val partial = File(directory, "$key.part")
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var thumbnail: Bitmap? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                staged.outputStream().use { copyBounded(input, it, 32L * 1024 * 1024) }
            } ?: error(context.getString(R.string.cover_image_unreadable))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(staged.path, bounds)
            require(bounds.outWidth in 1..50_000 && bounds.outHeight in 1..50_000) { context.getString(R.string.cover_image_unreadable) }
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                inPreferredConfig = Bitmap.Config.ARGB_8888
                while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1024) inSampleSize *= 2
            }
            decoded = BitmapFactory.decodeFile(staged.path, options) ?: error(context.getString(R.string.cover_image_unreadable))
            val orientation = runCatching { ExifInterface(staged.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val scale = minOf(1f, 720f / maxOf(oriented.width, oriented.height))
            thumbnail = Bitmap.createScaledBitmap(oriented, (oriented.width * scale).roundToInt().coerceAtLeast(1), (oriented.height * scale).roundToInt().coerceAtLeast(1), true)
            partial.outputStream().use { check(thumbnail.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            check(partial.renameTo(file(key))) { context.getString(R.string.cover_could_not_be_saved) }
            return key
        } finally {
            listOfNotNull(decoded, oriented, thumbnail).distinct().forEach { it.recycle() }
            staged.delete(); partial.delete()
        }
    }

    fun recover(retained: Set<String>) {
        staging.listFiles().orEmpty().filter(File::isFile).forEach { it.delete() }
        directory.listFiles().orEmpty().filter {
            it.isFile && (it.extension == "part" || (it.extension == "png" && it.nameWithoutExtension !in retained))
        }.forEach { it.delete() }
    }
}
