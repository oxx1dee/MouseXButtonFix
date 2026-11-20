package com.mousexbuttonfix;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * Mirrors how Forge loads mods: LWJGL is loaded by the parent class loader and the mod
 * classes come from a child class loader. Verifies that the wrapper can still be
 * installed into {@code Mouse.implementation} across that boundary.
 */
public final class LoaderTest {

    private static final int WM_XBUTTONDOWN = 0x020B;
    private static final int WM_XBUTTONUP = 0x020C;
    private static final int MK_SHIFT = 0x0004;
    private static final int MK_XBUTTON1 = 0x0020;
    private static final int XBUTTON1 = 1;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("pass the directory or jar that contains the mod classes");
        }

        Display.setTitle("MouseXButtonFixLoaderTest");
        Display.setDisplayMode(new DisplayMode(320, 240));
        Display.create();
        try {
            Mouse.create();
            try {
                URL childUrl = new File(args[0]).toURI().toURL();
                URLClassLoader child = new URLClassLoader(new URL[]{childUrl}, LoaderTest.class.getClassLoader());

                Class<?> installer = Class.forName("com.mousexbuttonfix.MouseFixInstaller", true, child);
                System.out.println("mod class loaded by: " + installer.getClassLoader());
                Method tryInstall = installer.getDeclaredMethod("tryInstall", String.class);
                tryInstall.setAccessible(true);
                tryInstall.invoke(null, "loader test");

                Field field = Mouse.class.getDeclaredField("implementation");
                field.setAccessible(true);
                Object implementation = field.get(null);
                System.out.println("mouse implementation: " + implementation.getClass().getName()
                        + " (proxy=" + Proxy.isProxyClass(implementation.getClass())
                        + ", loader=" + implementation.getClass().getClassLoader() + ")");
                check("wrapper installed", true, Proxy.isProxyClass(implementation.getClass()));

                Function postMessage = NativeLibrary.getInstance("user32")
                        .getFunction("PostMessageA", altConvention());
                Pointer hwnd = NativeLibrary.getInstance("user32")
                        .getFunction("FindWindowA", altConvention())
                        .invokePointer(new Object[]{null, "MouseXButtonFixLoaderTest"});
                check("window handle found", true, hwnd != null);

                postMessage.invokeInt(new Object[]{hwnd, Integer.valueOf(WM_XBUTTONDOWN),
                        Integer.valueOf((XBUTTON1 << 16) | MK_SHIFT | MK_XBUTTON1), Integer.valueOf(0)});
                postMessage.invokeInt(new Object[]{hwnd, Integer.valueOf(WM_XBUTTONUP),
                        Integer.valueOf((XBUTTON1 << 16) | MK_SHIFT), Integer.valueOf(0)});

                Display.processMessages();
                StringBuilder events = new StringBuilder();
                while (Mouse.next()) {
                    if (Mouse.getEventButton() < 0) {
                        continue;
                    }
                    if (events.length() > 0) {
                        events.append(' ');
                    }
                    events.append(Mouse.getEventButton()).append(Mouse.getEventButtonState() ? '+' : '-');
                }
                check("shift+mouse4 is mouse4 across class loaders", "3+ 3-", events.toString());
            } finally {
                Mouse.destroy();
            }
        } finally {
            Display.destroy();
        }
        System.out.println("LOADER TEST PASSED");
    }

    private static int altConvention() throws Exception {
        return Function.class.getField("ALT_CONVENTION").getInt(null);
    }

    private static void check(String name, Object expected, Object actual) {
        if (expected.equals(actual)) {
            System.out.println("PASS  " + name);
        } else {
            System.out.println("FAIL  " + name + " -> expected <" + expected + "> but was <" + actual + ">");
            System.exit(1);
        }
    }
}
