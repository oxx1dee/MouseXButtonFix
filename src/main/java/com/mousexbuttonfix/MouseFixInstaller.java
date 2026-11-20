package com.mousexbuttonfix;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.InputImplementation;

/**
 * Replaces {@code Mouse.implementation} with an {@link XButtonMouseWrapper} once LWJGL
 * has created its mouse.
 */
public final class MouseFixInstaller {

    private static Field implementationField;
    private static Object installedProxy;
    private static boolean disabled;
    private static boolean waitingLogged;

    private MouseFixInstaller() {
    }

    /** Called once per client tick; installs the wrapper as soon as that is possible. */
    public static void tick() {
        tryInstall("client tick");
    }

    static void tryInstall(String reason) {
        if (disabled) {
            return;
        }
        if (!isWindows()) {
            disabled = true;
            Log.info("Only Windows needs this fix, nothing to do on " + System.getProperty("os.name") + ".");
            return;
        }
        try {
            if (!Mouse.isCreated()) {
                if (!waitingLogged) {
                    waitingLogged = true;
                    Log.info("Waiting for LWJGL to create the mouse.");
                }
                return;
            }
            install();
        } catch (Throwable t) {
            disabled = true;
            Log.warnOnce("install", "Could not install the mouse button fix (" + reason
                    + "); the game will behave exactly as without the mod.", t);
        }
    }

    private static void install() throws Exception {
        Field field = implementationField();
        Object current = field.get(null);
        if (current == null) {
            return;
        }
        if (current == installedProxy) {
            return;
        }
        if (Proxy.isProxyClass(current.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(current);
            if (handler instanceof XButtonMouseWrapper) {
                // Already wrapped (for example by an earlier attempt): nothing to do.
                installedProxy = current;
                return;
            }
        }
        if (!(current instanceof InputImplementation)) {
            disabled = true;
            Log.warn("Unexpected mouse implementation " + current.getClass().getName() + "; not installing the fix.");
            return;
        }

        Object proxy = Proxy.newProxyInstance(
                InputImplementation.class.getClassLoader(),
                new Class<?>[]{InputImplementation.class},
                new XButtonMouseWrapper((InputImplementation) current, JnaPhysicalState.INSTANCE));

        field.set(null, proxy);
        installedProxy = proxy;
        Log.info("Mouse button fix installed over " + current.getClass().getName() + ".");
    }

    private static Field implementationField() throws NoSuchFieldException {
        if (implementationField == null) {
            Field field = Mouse.class.getDeclaredField("implementation");
            field.setAccessible(true);
            implementationField = field;
        }
        return implementationField;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("win");
    }
}
