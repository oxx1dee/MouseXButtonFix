package com.mousexbuttonfix;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.InputImplementation;

/**
 * Wraps LWJGL's {@link InputImplementation} and repairs the side mouse buttons.
 *
 * <p>The bug lives in {@code org.lwjgl.opengl.WindowsDisplay}, which decides which side
 * button was pressed like this:</p>
 *
 * <pre>
 *   case WM_XBUTTONDOWN:
 *       if ((wParam &amp; 0xFF) == MK_XBUTTON1) handleMouseButton(3, 1, millis);
 *       else                                  handleMouseButton(4, 1, millis);
 * </pre>
 *
 * <p>The low word of {@code wParam} does not only contain {@code MK_XBUTTON1}, it also
 * contains the modifier state ({@code MK_SHIFT}, {@code MK_CONTROL}, {@code MK_LBUTTON},
 * {@code MK_RBUTTON}, {@code MK_MBUTTON}). As soon as one of those bits is set the
 * comparison fails and a press of Mouse4 is delivered as Mouse5. The release
 * ({@code WM_XBUTTONUP}) reads the high word instead and is correct, so LWJGL ends up
 * with a "Mouse5 pressed, Mouse4 released" event pair and a permanently stuck Mouse5
 * state.</p>
 *
 * <p>For Forge/Minecraft the LWJGL classes are loaded by the system class loader, so the
 * class cannot be patched. Instead {@code Mouse.implementation} is replaced with a
 * dynamic proxy around the real implementation. Every call is forwarded unchanged; only
 * the mouse button information is corrected:</p>
 *
 * <ul>
 *   <li>{@code readMouse}: a "Mouse5 pressed" event is turned into "Mouse4 pressed" when
 *       the hardware state shows that Mouse4 is the button that was pressed.</li>
 *   <li>{@code pollMouse}: the held state of buttons 3 and 4 is taken from the hardware,
 *       which also clears the stuck Mouse5 state described above.</li>
 * </ul>
 *
 * <p>Only button 4 -&gt; button 3 is ever corrected, never the other way round, and when
 * the state is ambiguous the event is left exactly as LWJGL produced it. A genuine
 * Mouse5 press therefore keeps working in every situation.</p>
 */
final class XButtonMouseWrapper implements InvocationHandler {

    /** LWJGL button index of the first side button (Mouse4 / XBUTTON1). */
    private static final int BUTTON_MOUSE4 = 3;

    /** LWJGL button index of the second side button (Mouse5 / XBUTTON2). */
    private static final int BUTTON_MOUSE5 = 4;

    private static final int OFFSET_BUTTON = 0;
    private static final int OFFSET_STATE = 1;

    private static final String METHOD_READ_MOUSE = "readMouse";
    private static final String METHOD_POLL_MOUSE = "pollMouse";

    private final InputImplementation delegate;
    private final PhysicalState physical;

    /** Our own view of the side buttons, rebuilt from the (repaired) event stream. */
    private boolean mouse4Held;
    private boolean mouse5Held;

    XButtonMouseWrapper(InputImplementation delegate, PhysicalState physical) {
        this.delegate = delegate;
        this.physical = physical;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        if (METHOD_READ_MOUSE.equals(name) && args != null && args.length == 1 && args[0] instanceof ByteBuffer) {
            ByteBuffer buffer = (ByteBuffer) args[0];
            int start = buffer.position();
            Object result = callDelegate(method, args);
            try {
                repairEvents(buffer, start);
            } catch (Throwable t) {
                Log.warnOnce("readMouse", "Could not repair the mouse event stream: " + t, t);
            }
            return result;
        }

        if (METHOD_POLL_MOUSE.equals(name) && args != null && args.length == 2 && args[1] instanceof ByteBuffer) {
            Object result = callDelegate(method, args);
            try {
                repairPolledButtons((ByteBuffer) args[1]);
            } catch (Throwable t) {
                Log.warnOnce("pollMouse", "Could not repair the polled mouse button state: " + t, t);
            }
            return result;
        }

        return callDelegate(method, args);
    }

    private Object callDelegate(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw cause != null ? cause : e;
        }
    }

    /**
     * Rewrites the events the delegate just appended to {@code buffer}.
     *
     * @param buffer the buffer LWJGL reads mouse events from (in fill mode)
     * @param start  the buffer position before the delegate was called
     */
    private void repairEvents(ByteBuffer buffer, int start) {
        final int eventSize = Mouse.EVENT_SIZE;
        int end = buffer.position();
        if (end - start < eventSize) {
            return;
        }

        boolean havePhysical = physical.available();
        boolean mouse4Down = havePhysical && physical.xButton1Down();
        boolean mouse5Down = havePhysical && physical.xButton2Down();
        if (havePhysical) {
            // A button that is not physically down cannot still be held, so this also
            // recovers from events that were dropped (LWJGL clears its queue on grab).
            if (!mouse4Down) {
                mouse4Held = false;
            }
            if (!mouse5Down) {
                mouse5Held = false;
            }
        }

        for (int offset = start; offset + eventSize <= end; offset += eventSize) {
            int button = buffer.get(offset + OFFSET_BUTTON) & 0xFF;
            int state = buffer.get(offset + OFFSET_STATE) & 0xFF;

            if (button == BUTTON_MOUSE5 && state != 0
                    && isReallyMouse4(buffer, offset, end, eventSize, havePhysical, mouse4Down, mouse5Down)) {
                buffer.put(offset + OFFSET_BUTTON, (byte) BUTTON_MOUSE4);
                button = BUTTON_MOUSE4;
            }

            if (button == BUTTON_MOUSE4) {
                mouse4Held = state != 0;
            } else if (button == BUTTON_MOUSE5) {
                mouse5Held = state != 0;
            }
        }
    }

    /**
     * Decides whether a "Mouse5 pressed" event really is a Mouse4 press.
     *
     * @return {@code true} only when there is positive evidence; ambiguous events stay
     *         untouched so that a real Mouse5 press is never broken.
     */
    private boolean isReallyMouse4(ByteBuffer buffer, int offset, int end, int eventSize,
                                   boolean havePhysical, boolean mouse4Down, boolean mouse5Down) {
        if (havePhysical) {
            if (mouse4Down && !mouse5Down) {
                return true;  // only the first side button is held
            }
            if (mouse5Down && !mouse4Down) {
                return false; // only the second side button is held
            }
            if (mouse4Down) {
                // Both are held. Mouse5 cannot be pressed twice without being released
                // in between, so either Mouse5 is already down or another Mouse5 press
                // follows and this one has to belong to Mouse4.
                if (mouse5Held) {
                    return true;
                }
                return isFollowedByMouse5Press(buffer, offset, end, eventSize);
            }
        }

        // Mouse5 is already down according to the event stream, so this cannot be a new
        // Mouse5 press either.
        if (mouse5Held) {
            return true;
        }

        return isFollowedByMouse4Release(buffer, offset, end, eventSize);
    }

    /**
     * @return whether another Mouse5 press is part of the same batch without a Mouse5
     *         release in between, which proves that two different buttons were pressed.
     */
    private boolean isFollowedByMouse5Press(ByteBuffer buffer, int offset, int end, int eventSize) {
        for (int next = offset + eventSize; next + eventSize <= end; next += eventSize) {
            int button = buffer.get(next + OFFSET_BUTTON) & 0xFF;
            if (button == BUTTON_MOUSE5) {
                return (buffer.get(next + OFFSET_STATE) & 0xFF) != 0;
            }
        }
        return false;
    }

    /**
     * LWJGL reports the release of Mouse4 correctly as button 3. When a "Mouse5 pressed"
     * event is followed by a button 3 release before any button 4 release, the press was
     * a Mouse4 press that had its modifiers set.
     */
    private boolean isFollowedByMouse4Release(ByteBuffer buffer, int offset, int end, int eventSize) {
        if (mouse4Held) {
            // A button 3 release would belong to the press that is already held.
            return false;
        }
        for (int next = offset + eventSize; next + eventSize <= end; next += eventSize) {
            int button = buffer.get(next + OFFSET_BUTTON) & 0xFF;
            int state = buffer.get(next + OFFSET_STATE) & 0xFF;
            if (state == 0) {
                if (button == BUTTON_MOUSE4) {
                    return true;
                }
                if (button == BUTTON_MOUSE5) {
                    return false;
                }
            } else if (button == BUTTON_MOUSE5) {
                return false;
            }
        }
        return false;
    }

    /**
     * Replaces the held state of both side buttons with the state of the hardware.
     *
     * <p>LWJGL fills that buffer from the same broken message handling, so after
     * "Shift + Mouse4" it claims Mouse5 is held forever.</p>
     */
    private void repairPolledButtons(ByteBuffer buttons) {
        if (!physical.available()) {
            return;
        }
        boolean mouse4Down = physical.xButton1Down();
        boolean mouse5Down = physical.xButton2Down();

        if (!mouse4Down) {
            mouse4Held = false;
        }
        if (!mouse5Down) {
            mouse5Held = false;
        }

        int base = buttons.position();
        int limit = buttons.limit();
        if (base + BUTTON_MOUSE4 < limit) {
            buttons.put(base + BUTTON_MOUSE4, (byte) (mouse4Down ? 1 : 0));
        }
        if (base + BUTTON_MOUSE5 < limit) {
            buttons.put(base + BUTTON_MOUSE5, (byte) (mouse5Down ? 1 : 0));
        }
    }
}
