package com.mousexbuttonfix;

import java.util.HashSet;
import java.util.Set;

/**
 * Minimal logging helper.
 *
 * <p>System.out is enough here: it ends up in the normal Minecraft log and avoids a
 * compile time dependency on the log4j version that a given Minecraft version ships.</p>
 */
final class Log {

    private static final String PREFIX = "[MouseXButtonFix] ";
    private static final Set<String> REPORTED = new HashSet<String>();

    private Log() {
    }

    static void info(String message) {
        System.out.println(PREFIX + message);
    }

    static void warn(String message) {
        System.out.println(PREFIX + "WARNING: " + message);
    }

    static void warnOnce(String key, String message, Throwable cause) {
        synchronized (REPORTED) {
            if (!REPORTED.add(key)) {
                return;
            }
        }
        System.out.println(PREFIX + "WARNING: " + message);
        if (cause != null) {
            cause.printStackTrace(System.out);
        }
    }
}
