package dev.soto.panelgrove.engine;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
interface IDocumentDecoder {
    oneway void abort();
    Bundle index(in ParcelFileDescriptor source, String kind);
    Bundle extract(in ParcelFileDescriptor source, String entry, in ParcelFileDescriptor destination, long limit);
    Bundle renderPdf(in ParcelFileDescriptor source, int page, int width, int height,
        float left, float top, float right, float bottom, in ParcelFileDescriptor destination);
}
