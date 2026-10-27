package dev.soto.panelgrove.engine

import dev.soto.panelgrove.R
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import dev.soto.panelgrove.core.ArchiveRules
import java.io.FileOutputStream

object NativeArchive {
    init { System.loadLibrary("panelgrove_archive") }
    external fun list(fd: Int): Array<String>
    external fun extract(fd: Int, entry: String, destination: Int, limit: Long)
}

class DecodeService : Service() {
    private val strings get() = dev.soto.panelgrove.ui.StringDictionary(resources)
    // Checked JNI exceptions cannot safely cross Binder's exception protocol. Always send
    // an explicit reply, including failures, instead of leaving the client an empty Parcel.
    private inline fun reply(block: () -> Bundle): Bundle = try { block() }
    catch (error: Exception) { failure(error) }
    catch (error: LinkageError) { failure(error) }
    private fun failure(error: Throwable): Bundle {
        android.util.Log.e("PanelgroveDecoder", strings[R.string.document_decoding_failed], error)
        return Bundle().apply { putString("error", error.message ?: error.javaClass.simpleName) }
    }
    private val decoder = object : IDocumentDecoder.Stub() {
        override fun abort() { android.os.Process.killProcess(android.os.Process.myPid()) }
        override fun index(source: ParcelFileDescriptor, kind: String): Bundle = reply { source.use {
            if (kind == "RAR" || kind == "SEVEN_Z") Bundle().apply { putStringArray("entries", NativeArchive.list(source.fd)) }
            else PdfRenderer(ParcelFileDescriptor.dup(source.fileDescriptor)).use { pdf ->
                require(pdf.pageCount in 1..ArchiveRules.MAX_ENTRIES) { strings[R.string.pdf_contains_too_many_pages] }
                val widths = IntArray(pdf.pageCount); val heights = IntArray(pdf.pageCount)
                for (i in 0 until pdf.pageCount) pdf.openPage(i).use { page -> widths[i] = page.width; heights[i] = page.height }
                Bundle().apply { putIntArray("widths", widths); putIntArray("heights", heights) }
            }
        } }
        override fun extract(source: ParcelFileDescriptor, entry: String, destination: ParcelFileDescriptor, limit: Long): Bundle = reply {
            source.use { destination.use {
                NativeArchive.extract(source.fd, entry, destination.fd, limit.coerceIn(1, ArchiveRules.MAX_PAGE_BYTES))
            } }
            Bundle()
        }
        override fun renderPdf(source: ParcelFileDescriptor, page: Int, width: Int, height: Int,
            left: Float, top: Float, right: Float, bottom: Float, destination: ParcelFileDescriptor): Bundle = reply {
            source.use { destination.use {
                require(width in 1..2048 && height in 1..3072 && left >= 0 && top >= 0 && right <= 1 && bottom <= 1 && right > left && bottom > top)
                PdfRenderer(ParcelFileDescriptor.dup(source.fileDescriptor)).use { pdf -> pdf.openPage(page).use { p ->
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        val sx = width.toFloat() / (p.width * (right - left))
                        val sy = height.toFloat() / (p.height * (bottom - top))
                        val matrix = Matrix().apply { setValues(floatArrayOf(sx, 0f, -left * p.width * sx, 0f, sy, -top * p.height * sy, 0f, 0f, 1f)) }
                        p.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        FileOutputStream(destination.fileDescriptor).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { bitmap.recycle() }
                } }
            } }
            Bundle()
        }
    }
    override fun onBind(intent: Intent) = decoder
}
