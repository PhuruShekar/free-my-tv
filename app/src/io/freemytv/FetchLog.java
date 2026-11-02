package io.freemytv;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * One line per fetch, kept across restarts (adb logcat holds only minutes): when, which source, whether it worked.
 * Read it with `adb shell cat /sdcard/Android/data/io.freemytv/files/fetch.log`. Never a key or an address.
 */
final class FetchLog {
    private FetchLog() {}

    private static final long MAX = 64 * 1024;
    private static File file;
    private static final SimpleDateFormat STAMP = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);   // used only inside write()

    static synchronized void init(Context c) {
        if (file != null) return;
        File dir = c.getExternalFilesDir(null);
        file = new File(dir != null ? dir : c.getFilesDir(), "fetch.log");
    }

    static void ok(String source, long sinceNanos) {
        write(source + " ok " + (System.nanoTime() - sinceNanos) / 1_000_000 + " ms");
    }

    static void fail(String source, Exception e) {
        write(source + " FAILED " + describe(e));
    }

    /** What went wrong, short and with no address: the exception's name, and the status for an HTTP error. */
    static String describe(Exception e) {
        return e.getClass().getSimpleName() + (e instanceof IOException && e.getMessage() != null && e.getMessage().startsWith("HTTP") ? " " + e.getMessage() : "");
    }

    static void note(String source, String text) {
        write(source + " " + text);
    }

    /** When the log fills it becomes `fetch.log.1` (the one before is dropped) and a new one starts. */
    private static synchronized void write(String line) {
        if (file == null) return;
        try {
            if (file.length() > MAX) {
                File older = new File(file.getPath() + ".1");
                older.delete();
                file.renameTo(older);
            }
            String stamp = STAMP.format(new Date());
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write((stamp + " " + line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }
}
