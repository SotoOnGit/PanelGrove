package dev.soto.panelgrove.engine

import dev.soto.panelgrove.R
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.Executors

class DecoderClient(private val context: Context) {
    private val strings get() = dev.soto.panelgrove.ui.StringDictionary(context.resources)
    suspend fun request(block: (IDocumentDecoder) -> Bundle?): Bundle {
        val reply = call(block) ?: throw java.io.IOException(strings[R.string.the_document_decoder_returned_no_response_try_opening_the])
        reply.getString("error")?.let { throw java.io.IOException(it) }
        return reply
    }
    private val mutex = Mutex()
    private var service: IDocumentDecoder? = null
    private var connection: ServiceConnection? = null
    private val calls = Mutex()
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "panelgrove-decoder").apply { isDaemon = true } }
    /** Binder work cannot be interrupted by cancelling an IO coroutine. Cancel the isolated
     * process instead, releasing native/PDF work and allowing the next request to rebind. */
    suspend fun <T> call(block: (IDocumentDecoder) -> T): T = calls.withLock {
        val api = get()
        val result = withTimeoutOrNull(60_000) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { runCatching { api.abort() } }
                worker.execute {
                    if (continuation.isActive) try { continuation.resume(Result.success(block(api))) }
                    catch (error: Exception) { continuation.resumeWithException(error) }
                }
            }
        }
        (result ?: error(strings[R.string.the_document_decoder_took_too_long_try_the_page])).getOrThrow()
    }
    suspend fun get(): IDocumentDecoder = mutex.withLock {
        service?.takeIf { it.asBinder().isBinderAlive }?.let { return@withLock it }
        connection?.let { runCatching { context.unbindService(it) } }
        val ready = CompletableDeferred<IDocumentDecoder>()
        val binding = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { ready.complete(IDocumentDecoder.Stub.asInterface(binder)) }
            override fun onServiceDisconnected(name: ComponentName) { if (connection === this) service = null; ready.completeExceptionally(IllegalStateException(strings[R.string.decoder_stopped_try_the_page_again])) }
            override fun onBindingDied(name: ComponentName) { if (connection === this) service = null; ready.completeExceptionally(IllegalStateException(strings[R.string.decoder_stopped_open_the_book_again])) }
            override fun onNullBinding(name: ComponentName) { ready.completeExceptionally(IllegalStateException(strings[R.string.decoder_is_unavailable])) }
        }
        connection = binding
        check(context.bindService(Intent(context, DecodeService::class.java), binding, Context.BIND_AUTO_CREATE)) { strings[R.string.cannot_start_the_document_decoder] }
        try { (withTimeoutOrNull(15_000) { ready.await() } ?: error(strings[R.string.the_document_decoder_did_not_start_try_again])).also { service = it } }
        catch (error: Exception) { runCatching { context.unbindService(binding) }; connection = null; throw error }
    }
}
