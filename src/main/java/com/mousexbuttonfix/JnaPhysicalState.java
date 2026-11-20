package com.mousexbuttonfix;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;

/**
 * Reads the physical side button state with {@code user32!GetAsyncKeyState}.
 *
 * <p>JNA is shipped with the game (3.4.0 in 1.8.9, 4.4.0 in 1.12.2), so no native
 * code of our own is required.</p>
 */
final class JnaPhysicalState implements PhysicalState {

    static final PhysicalState INSTANCE = new JnaPhysicalState();

    private static final int VK_XBUTTON1 = 0x05;
    private static final int VK_XBUTTON2 = 0x06;

    /** The high bit of GetAsyncKeyState's result means "currently down". */
    private static final int DOWN_MASK = 0x8000;

    private Function getAsyncKeyState;
    private boolean initialised;
    private boolean available;

    private JnaPhysicalState() {
    }

    @Override
    public boolean available() {
        initialise();
        return available;
    }

    @Override
    public boolean xButton1Down() {
        return isDown(VK_XBUTTON1);
    }

    @Override
    public boolean xButton2Down() {
        return isDown(VK_XBUTTON2);
    }

    private boolean isDown(int virtualKey) {
        initialise();
        Function function = getAsyncKeyState;
        if (function == null) {
            return false;
        }
        try {
            int result = function.invokeInt(new Object[]{Integer.valueOf(virtualKey)});
            return (result & DOWN_MASK) != 0;
        } catch (Throwable t) {
            getAsyncKeyState = null;
            available = false;
            Log.warnOnce("jna-call", "Could not read the side mouse buttons any more; the fix is now inactive.", t);
            return false;
        }
    }

    private void initialise() {
        if (initialised) {
            return;
        }
        initialised = true;
        try {
            // ALT_CONVENTION is 1 in JNA 3.x but 63 in JNA 4.x, so it has to be read from
            // whichever version of JNA the game actually loaded instead of being inlined.
            int altConvention = Function.class.getField("ALT_CONVENTION").getInt(null);
            NativeLibrary user32 = NativeLibrary.getInstance("user32");
            getAsyncKeyState = user32.getFunction("GetAsyncKeyState", altConvention);
            available = true;
            Log.info("Physical side button state available (user32!GetAsyncKeyState).");
        } catch (Throwable t) {
            available = false;
            getAsyncKeyState = null;
            Log.warnOnce("jna-init", "JNA/user32 is unavailable; only clicks that finish within a single "
                    + "frame can be repaired.", t);
        }
    }
}
