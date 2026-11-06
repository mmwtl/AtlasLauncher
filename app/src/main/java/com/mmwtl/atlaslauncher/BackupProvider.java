package com.mmwtl.atlaslauncher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Serves the latest settings export to the app picked in the share sheet, like GInputBridge's
 * file provider for .gibb. Head units have no file picker, so the backup travels as a shared
 * file; access is granted per share intent.
 */
public final class BackupProvider extends ContentProvider {
    private static final String DIR = "export";

    /** Writes the backup under a dated name and returns its shareable uri. */
    static Uri publish(Context context, String name, String content) throws IOException {
        File dir = new File(context.getCacheDir(), DIR);
        File[] old = dir.listFiles();
        if (old != null) {
            for (File file : old) {
                file.delete();
            }
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return new Uri.Builder().scheme("content").authority(authority(context))
                .appendPath(name).build();
    }

    static String authority(Context context) {
        return context.getPackageName() + ".backup";
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File file(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.startsWith(".")) {
            throw new FileNotFoundException(String.valueOf(uri));
        }
        File file = new File(new File(getContext().getCacheDir(), DIR), name);
        if (!file.isFile()) {
            throw new FileNotFoundException(String.valueOf(uri));
        }
        return file;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new SecurityException("Read-only");
        }
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        File file;
        try {
            file = file(uri);
        } catch (FileNotFoundException error) {
            return null;
        }
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[index])) {
                row[index] = file.getName();
            } else if (OpenableColumns.SIZE.equals(columns[index])) {
                row[index] = file.length();
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return SettingsBackup.MIME;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
