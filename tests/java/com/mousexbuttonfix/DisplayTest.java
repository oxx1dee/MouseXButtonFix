package com.mousexbuttonfix;

import java.lang.reflect.Field;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * End to end check against real LWJGL with a real window: posts the window messages that
 * Windows produces for the side buttons and verifies the events Minecraft would see.
 */
public final class DisplayTest {

    private static final int WM_XBUTTONDOWN = 0x020B;
    private static final int WM_XBUTTONUP = 0x020C;
    private static final int MK_SHIFT = 0x0004;
    private static final int MK_XBUTTON1 = 0x0020;
    private static final int MK_XBUTTON2 = 0x0040;
    private static final int XBUTTON1 = 1;
    private static final int XBUTTON2 = 2;

    private static Function postMessage;
    private static int failures;

    public static void main(String[] args) throws Exception {
        Display.setTitle("MouseXButtonFixTest");
        Display.setDisplayMode(new DisplayMode(320, 240));
        Display.create();
        try {
            Mouse.create();
            try {
                Pointer hwnd = findWindow("MouseXButtonFixTest");
                check("window handle found", true, hwnd != null);
                if (hwnd == null) {
                    return;
                }
                postMessage = NativeLibrary.getInstance("user32").getFunction("PostMessageA", altConvention());
                System.out.println("LWJGL reports " + Mouse.getButtonCount() + " mouse buttons");

                System.out.println("implementation before: " + implementation());

                click(hwnd, XBUTTON1, true);
                check("unfixed: shift+mouse4 is reported as mouse5", "4+ 3-", events());
                if (Mouse.getButtonCount() >= 5) {
                    check("unfixed: mouse5 state is stuck", true, Mouse.isButtonDown(4));
                }

                MouseFixInstaller.tryInstall("display test");
                System.out.println("implementation after:  " + implementation());

                click(hwnd, XBUTTON1, true);
                check("fixed: shift+mouse4 is mouse4", "3+ 3-", events());
                if (Mouse.getButtonCount() >= 5) {
                    check("fixed: no stuck mouse5 state", false, Mouse.isButtonDown(4));
                }

                click(hwnd, XBUTTON2, true);
                check("fixed: shift+mouse5 is still mouse5", "4+ 4-", events());

                click(hwnd, XBUTTON1, false);
                check("fixed: plain mouse4", "3+ 3-", events());

                click(hwnd, XBUTTON2, false);
                check("fixed: plain mouse5", "4+ 4-", events());
            } finally {
                Mouse.destroy();
            }
        } finally {
            Display.destroy();
        }

        if (failures == 0) {
            System.out.println("DISPLAY TEST PASSED");
        } else {
            System.out.println(failures + " DISPLAY CHECK(S) FAILED");
            System.exit(1);
        }
    }

    private static void click(Pointer hwnd, int button, boolean shiftHeld) {
        int modifiers = shiftHeld ? MK_SHIFT : 0;
        int downState = modifiers | (button == XBUTTON1 ? MK_XBUTTON1 : MK_XBUTTON2);
        post(hwnd, WM_XBUTTONDOWN, (button << 16) | downState);
        post(hwnd, WM_XBUTTONUP, (button << 16) | modifiers);
    }

    private static void post(Pointer hwnd, int message, int wParam) {
        int result = postMessage.invokeInt(new Object[]{
                hwnd, Integer.valueOf(message), Integer.valueOf(wParam), Integer.valueOf(0)});
        if (result == 0) {
            System.out.println("PostMessage failed for 0x" + Integer.toHexString(message));
        }
    }

    private static String events() {
        Display.processMessages();   // also polls the mouse, like Minecraft does

        StringBuilder text = new StringBuilder();
        while (Mouse.next()) {
            int button = Mouse.getEventButton();
            if (button < 0) {
                continue;
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(button).append(Mouse.getEventButtonState() ? '+' : '-');
        }
        return text.toString();
    }

    private static Pointer findWindow(String title) throws Exception {
        Function findWindow = NativeLibrary.getInstance("user32").getFunction("FindWindowA", altConvention());
        return findWindow.invokePointer(new Object[]{null, title});
    }

    private static int altConvention() throws Exception {
        Field field = Function.class.getField("ALT_CONVENTION");
        return field.getInt(null);
    }

    private static String implementation() throws Exception {
        Field field = Mouse.class.getDeclaredField("implementation");
        field.setAccessible(true);
        return String.valueOf(field.get(null));
    }

    private static void check(String name, Object expected, Object actual) {
        if (expected.equals(actual)) {
            System.out.println("PASS  " + name);
        } else {
            failures++;
            System.out.println("FAIL  " + name + " -> expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
