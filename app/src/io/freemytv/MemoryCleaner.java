package io.freemytv;

import android.content.Context;

import java.io.IOException;

/** Stops the apps the person installed and the TV's video apps, through the TV's own ADB (never Free My TV or system pieces). */
final class MemoryCleaner {
    private MemoryCleaner() {}

    interface Done { void result(String text); }

    /** Video apps that come with the TV: they hold the most memory and are the ones that get stuck. */
    private static final String PRELOADED_VIDEO = "com.netflix.ninja com.google.android.youtube.tv";
    private static boolean running;

    /** Runs in the background; `done` gets the text to show ("Freed 57 MB", "Done" or a problem) on that thread. */
    static synchronized void run(Context c, Done done) {
        if (running) return;
        running = true;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            String result;
            try (Adb adb = Adb.connect(app, 10_000, null)) {
                long before = availableKb(adb);
                adb.shell("for p in $(pm list packages -3 | sed 's/package://') " + PRELOADED_VIDEO + "; do "
                        + "[ \"$p\" = io.freemytv ] || am force-stop $p; done; sleep 2");
                long freed = (availableKb(adb) - before) / 1024;
                result = freed > 0 ? Ui.trf("Freed {n} MB", "n", freed) : Ui.tr("Done");
            } catch (IOException e) {
                result = Ui.tr("Can't reach the TV's debugging");
            }
            synchronized (MemoryCleaner.class) { running = false; }
            done.result(result);
        }, "free-memory").start();
    }

    private static long availableKb(Adb adb) throws IOException {
        for (String line : adb.shell("cat /proc/meminfo").split("\n")) {
            if (line.startsWith("MemAvailable:")) return Long.parseLong(line.replaceAll("[^0-9]", ""));
        }
        return 0;
    }
}
