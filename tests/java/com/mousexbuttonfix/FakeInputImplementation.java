package com.mousexbuttonfix;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.InputImplementation;

/**
 * Stand-in for LWJGL's {@code WindowsDisplay}/{@code WindowsMouse} that reproduces the
 * bug exactly as it happens in the real implementation.
 */
final class FakeInputImplementation implements InputImplementation {

    private static final int EVENT_SIZE = 22;

    private final ByteBuffer queued = ByteBuffer.allocate(EVENT_SIZE * 200);
    private final byte[] buttonStates = new byte[5];

    /** Emulates {@code WindowsDisplay.handleMessage} for WM_XBUTTON[DOWN|UP]. */
    void xButton(boolean firstButton, boolean down, boolean modifierHeld) {
        int button;
        if (down) {
            // The bug: (wParam & 0xFF) == MK_XBUTTON1 fails as soon as a modifier bit is set.
            button = (firstButton && !modifierHeld) ? 3 : 4;
        } else {
            // WM_XBUTTONUP uses the high word and is correct.
            button = firstButton ? 3 : 4;
        }
        queue(button, down ? 1 : 0);
    }

    private void queue(int button, int state) {
        ByteBuffer event = ByteBuffer.allocate(EVENT_SIZE);
        event.put((byte) button);
        event.put((byte) state);
        event.putInt(0);
        event.putInt(0);
        event.putInt(0);
        event.putLong(0L);
        event.flip();
        queued.put(event);
        if (button < buttonStates.length) {
            buttonStates[button] = (byte) (state != 0 ? 1 : 0);
        }
    }

    @Override
    public void pollMouse(IntBuffer coordBuffer, ByteBuffer buttons) {
        for (int i = 0; i < coordBuffer.remaining(); i++) {
            coordBuffer.put(coordBuffer.position() + i, 0);
        }
        for (int i = 0; i < buttonStates.length; i++) {
            if (buttons.position() + i < buttons.limit()) {
                buttons.put(buttons.position() + i, buttonStates[i]);
            }
        }
    }

    @Override
    public void readMouse(ByteBuffer buffer) {
        queued.flip();
        int copied = Math.min(buffer.remaining(), queued.remaining());
        int limit = queued.limit();
        queued.limit(queued.position() + copied);
        buffer.put(queued);
        queued.limit(limit);
        queued.compact();
    }

    /** Parses the button/state pairs contained in a buffer filled by readMouse. */
    static List<int[]> events(ByteBuffer buffer) {
        List<int[]> result = new ArrayList<int[]>();
        for (int offset = 0; offset + EVENT_SIZE <= buffer.position(); offset += EVENT_SIZE) {
            result.add(new int[]{buffer.get(offset) & 0xFF, buffer.get(offset + 1) & 0xFF});
        }
        return result;
    }

    @Override
    public boolean hasWheel() {
        return true;
    }

    @Override
    public int getButtonCount() {
        return 5;
    }

    @Override
    public void createMouse() {
    }

    @Override
    public void destroyMouse() {
    }

    @Override
    public void grabMouse(boolean grab) {
    }

    @Override
    public int getNativeCursorCapabilities() {
        return 0;
    }

    @Override
    public void setCursorPosition(int x, int y) {
    }

    @Override
    public void setNativeCursor(Object handle) {
    }

    @Override
    public int getMinCursorSize() {
        return 0;
    }

    @Override
    public int getMaxCursorSize() {
        return 0;
    }

    @Override
    public void createKeyboard() {
    }

    @Override
    public void destroyKeyboard() {
    }

    @Override
    public void pollKeyboard(ByteBuffer keyDownBuffer) {
    }

    @Override
    public void readKeyboard(ByteBuffer buffer) {
    }

    @Override
    public Object createCursor(int width, int height, int xHotspot, int yHotspot, int numImages, IntBuffer images, IntBuffer delays) {
        return null;
    }

    @Override
    public void destroyCursor(Object cursorHandle) {
    }

    @Override
    public int getWidth() {
        return 854;
    }

    @Override
    public int getHeight() {
        return 480;
    }

    @Override
    public boolean isInsideWindow() {
        return true;
    }
}
