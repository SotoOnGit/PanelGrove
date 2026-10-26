package dev.soto.panelgrove;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

/** Runs in the test APK's own process, so deliberately has no dependency on the target APK's Kotlin runtime. */
public class FixtureDocumentsProvider extends ContentProvider {
    private File base;
    private final java.util.Set<String> denied = new java.util.HashSet<>();
    private final java.util.Set<String> fixedTime = new java.util.HashSet<>();
    private final java.util.Set<String> noTime = new java.util.HashSet<>();
    private void checkAccess(String id) { for (String prefix : denied) if (id.equals(prefix) || id.startsWith(prefix + "/")) throw new SecurityException("Fixture permission revoked"); }
    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        if ("android:createDocument".equals(method)) {
            Uri parent = extras.getParcelable("uri"); String id = DocumentsContract.getDocumentId(parent); checkAccess(id);
            String name = extras.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            if (name == null || name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) throw new IllegalArgumentException("Unsafe name");
            File directory = file(id); if (!directory.isDirectory()) throw new IllegalStateException("Missing parent");
            File child = new File(directory, name); int suffix = 2;
            while (child.exists()) child = new File(directory, name + " (" + suffix++ + ")");
            try {
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(extras.getString(DocumentsContract.Document.COLUMN_MIME_TYPE))) child.mkdir();
                else child.createNewFile();
            } catch (IOException error) { throw new IllegalStateException(error); }
            Bundle result = new Bundle(); result.putParcelable("uri", DocumentsContract.buildDocumentUri(parent.getAuthority(), id + "/" + child.getName())); return result;
        }
        if ("android:deleteDocument".equals(method)) {
            Uri uri = extras.getParcelable("uri"); String id = DocumentsContract.getDocumentId(uri); checkAccess(id);
            File target = file(id); if (!target.exists()) throw new IllegalStateException("Missing document"); remove(target); return Bundle.EMPTY;
        }
        if ("android:isChildDocument".equals(method)) {
            Uri parent = extras.getParcelable("uri"); Uri child = extras.getParcelable("android.content.extra.TARGET_URI");
            Bundle result = new Bundle(); result.putBoolean("result", isChildDocument(DocumentsContract.getDocumentId(parent), DocumentsContract.getDocumentId(child))); return result;
        }
        if (!"fixture".equals(method)) return super.call(method, arg, extras);
        String id = extras.getString("id", "hardening");
        if (!id.equals("hardening") && !id.startsWith("hardening/")) throw new IllegalArgumentException("Only generated hardening fixtures may be changed");
        File target = file(id);
        if (extras.getBoolean("grantTree")) {
            Uri tree = DocumentsContract.buildTreeDocumentUri("dev.soto.panelgrove.test.documents", id);
            getContext().grantUriPermission("dev.soto.panelgrove", tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        }
        if (extras.getBoolean("reset")) { remove(target); denied.clear(); fixedTime.clear(); noTime.clear(); }
        if (extras.containsKey("denied")) { if (extras.getBoolean("denied")) denied.add(id); else denied.remove(id); }
        if (extras.getBoolean("fixedTime")) fixedTime.add(id);
        if (extras.getBoolean("noTime")) noTime.add(id);
        try {
            if (extras.getBoolean("directory")) target.mkdirs();
            byte[] bytes = extras.getByteArray("bytes");
            if (bytes != null) { target.getParentFile().mkdirs(); try (OutputStream output = new FileOutputStream(target)) { output.write(bytes); } }
        } catch (IOException error) { throw new IllegalStateException(error); }
        return Bundle.EMPTY;
    }
    private static void remove(File target) { File[] children = target.listFiles(); if (children != null) for (File child : children) remove(child); if (target.exists() && !target.delete()) throw new IllegalStateException("Cannot reset fixture"); }
    @Override public boolean onCreate() {
        base = new File(getContext().getFilesDir(), "provider-fixtures"); base.mkdirs();
        try {
            try (InputStream input = getContext().getAssets().open("fixtures/sample.cbz"); OutputStream output = new FileOutputStream(new File(base, "sample.cbz"))) { copy(input, output); }
            File images = new File(base, "images"); images.mkdirs();
            try (ZipInputStream zip = new ZipInputStream(getContext().getAssets().open("fixtures/sample.cbz"))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) if (!entry.isDirectory() && !entry.getName().contains("/") && !entry.getName().startsWith("."))
                    try (OutputStream output = new FileOutputStream(new File(images, entry.getName()))) { copy(zip, output); }
            }
            return true;
        } catch (IOException error) { throw new IllegalStateException(error); }
    }
    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[65536]; int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
    }
    private File file(String id) {
        File target = id.equals("root") ? base : new File(base, id.startsWith("pipe:") ? id.substring(5) : id);
        try {
            if (!target.getCanonicalPath().equals(base.getCanonicalPath()) && !target.getCanonicalPath().startsWith(base.getCanonicalPath() + File.separator)) throw new IllegalArgumentException("Unsafe fixture path");
        } catch (IOException error) { throw new IllegalArgumentException(error); }
        return target;
    }
    public Cursor queryRoots(String[] projection) {
        String[] columns = projection != null ? projection : new String[] { DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID, DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_FLAGS };
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) row.add(column.equals(DocumentsContract.Root.COLUMN_ROOT_ID) ? "panelgrove-tests" : column.equals(DocumentsContract.Root.COLUMN_DOCUMENT_ID) ? "root" : column.equals(DocumentsContract.Root.COLUMN_TITLE) ? "Panelgrove test library" : column.equals(DocumentsContract.Root.COLUMN_FLAGS) ? DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD : null);
        return cursor;
    }
    private final String[] defaults = new String[] { DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_FLAGS };
    private void add(MatrixCursor cursor, String id) {
        checkAccess(id);
        File target = file(id); MatrixCursor.RowBuilder row = cursor.newRow();
        if (!target.exists()) throw new IllegalStateException("Fixture document was removed");
        for (String column : cursor.getColumnNames()) {
            Object value = null;
            if (column.equals(DocumentsContract.Document.COLUMN_DOCUMENT_ID)) value = id;
            else if (column.equals(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) value = id.equals("root") ? "Panelgrove test library" : target.getName();
            else if (column.equals(DocumentsContract.Document.COLUMN_MIME_TYPE)) value = target.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : target.getName().endsWith(".json") ? "application/json" : "application/zip";
            else if (column.equals(DocumentsContract.Document.COLUMN_SIZE)) value = target.length();
            else if (column.equals(DocumentsContract.Document.COLUMN_LAST_MODIFIED)) {
                String actual = id.startsWith("pipe:") ? id.substring(5) : id;
                value = noTime.contains(actual) ? null : fixedTime.contains(actual) ? 1234L : target.lastModified();
            }
            else if (column.equals(DocumentsContract.Document.COLUMN_FLAGS)) value = DocumentsContract.Document.FLAG_SUPPORTS_WRITE | DocumentsContract.Document.FLAG_SUPPORTS_DELETE | (target.isDirectory() ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE : 0);
            row.add(value);
        }
    }
    public Cursor queryDocument(String id, String[] projection) { MatrixCursor cursor = new MatrixCursor(projection != null ? projection : defaults); add(cursor, id); return cursor; }
    public Cursor queryChildDocuments(String parent, String[] projection, String sortOrder) {
        checkAccess(parent);
        if (!file(parent).isDirectory()) throw new IllegalStateException("Fixture folder was removed");
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : defaults);
        File[] children = file(parent).listFiles();
        if (children != null) for (File child : children) add(cursor, parent.equals("root") ? child.getName() : parent + "/" + child.getName());
        return cursor;
    }
    public boolean isChildDocument(String parent, String id) { try { return file(id).getCanonicalPath().startsWith(file(parent).getCanonicalPath() + File.separator); } catch (IOException error) { return false; } }
    public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws FileNotFoundException {
        checkAccess(id.startsWith("pipe:") ? id.substring(5) : id);
        if (id.startsWith("pipe:")) {
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
                new Thread(() -> { try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]); InputStream input = new FileInputStream(file(id))) { copy(input, output); } catch (IOException ignored) {} }).start();
                return pipe[0];
            } catch (IOException error) { throw new FileNotFoundException(error.toString()); }
        }
        return ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.parseMode(mode));
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        java.util.List<String> parts = uri.getPathSegments();
        if (parts.size() == 1 && parts.get(0).equals("root")) return queryRoots(projection);
        if (parts.get(parts.size() - 1).equals("children")) return queryChildDocuments(parts.get(parts.size() - 2), projection, sortOrder);
        return queryDocument(parts.get(parts.size() - 1), projection);
    }
    @Override public String getType(Uri uri) { return file(uri.getLastPathSegment()).isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : "application/octet-stream"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException { return openDocument(uri.getLastPathSegment(), mode, null); }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
