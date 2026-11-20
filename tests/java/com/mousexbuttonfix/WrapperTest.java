package com.mousexbuttonfix;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;

import org.lwjgl.opengl.InputImplementation;

/**
 * Exercises the event/state repair logic against a faithful copy of LWJGL's behaviour,
 * so the fix can be verified without starting the game.
 */
public final class WrapperTest {

    private static final int EVENT_SIZE = 22;

    private static int failures;

    public static void main(String[] args) throws Exception {
        shiftMouse4();
        mouse5WithShift();
        mouse4AndMouse5InTheSameFrame();
        mouse4HeldThenMouse5Pressed();
        mouse4ReleasedClearsStuckMouse5();
        fastMouse4Click();
        fastMouse5Click();
        plainMouse4WithoutModifier();
        mouse5WhileShiftHeldAndHeldDown();
        withoutPhysicalState();
        forwarding();

        System.out.println("JNA physical state available: " + JnaPhysicalState.INSTANCE.available());

        if (failures == 0) {
            System.out.println("ALL CHECKS PASSED");
        } else {
            System.out.println(failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    private static void shiftMouse4() throws Exception {
        Harness h = new Harness();
        h.physical.x1 = true;
        h.real.xButton(true, true, true);
        Frame f = h.frame();
        check("shift+mouse4: event rewritten to mouse4", "3+", describe(f.events));
        check("shift+mouse4: polled state", "b3=1 b4=0", polled(f));
    }

    private static void mouse5WithShift() throws Exception {
        Harness h = new Harness();
        h.physical.x2 = true;
        h.real.xButton(false, true, true);
        Frame f = h.frame();
        check("shift+mouse5: event stays mouse5", "4+", describe(f.events));
        check("shift+mouse5: polled state", "b3=0 b4=1", polled(f));
    }

    private static void mouse4AndMouse5InTheSameFrame() throws Exception {
        Harness h = new Harness();
        h.real.xButton(true, true, true);   // queued while shift was held
        h.real.xButton(false, true, true);
        h.physical.x1 = true;
        h.physical.x2 = true;
        Frame f = h.frame();
        check("mouse4+mouse5 in one frame: two distinct presses", "3+ 4+", describe(f.events));
    }

    private static void mouse4HeldThenMouse5Pressed() throws Exception {
        Harness h = new Harness();
        h.physical.x1 = true;
        h.real.xButton(true, true, true);
        Frame first = h.frame();
        check("mouse4 held: first frame", "3+", describe(first.events));

        h.physical.x2 = true;
        h.real.xButton(false, true, true);
        Frame second = h.frame();
        check("mouse5 pressed while mouse4 held", "4+", describe(second.events));
        check("mouse5 pressed while mouse4 held: polled state", "b3=1 b4=1", polled(second));
    }

    private static void mouse4ReleasedClearsStuckMouse5() throws Exception {
        Harness h = new Harness();
        h.physical.x1 = true;
        h.real.xButton(true, true, true);
        h.frame();

        h.physical.x1 = false;
        h.real.xButton(true, false, true);
        Frame f = h.frame();
        check("release of mouse4: event", "3-", describe(f.events));
        check("release of mouse4: no stuck mouse5 state", "b3=0 b4=0", polled(f));
    }

    private static void fastMouse4Click() throws Exception {
        Harness h = new Harness();
        h.real.xButton(true, true, true);
        h.real.xButton(true, false, true);
        Frame f = h.frame();
        check("fast mouse4 click (already released)", "3+ 3-", describe(f.events));
    }

    private static void fastMouse5Click() throws Exception {
        Harness h = new Harness();
        h.real.xButton(false, true, true);
        h.real.xButton(false, false, true);
        Frame f = h.frame();
        check("fast mouse5 click (already released)", "4+ 4-", describe(f.events));
    }

    private static void plainMouse4WithoutModifier() throws Exception {
        Harness h = new Harness();
        h.physical.x1 = true;
        h.real.xButton(true, true, false);
        Frame f = h.frame();
        check("mouse4 without modifiers", "3+", describe(f.events));
        check("mouse4 without modifiers: polled state", "b3=1 b4=0", polled(f));
    }

    private static void mouse5WhileShiftHeldAndHeldDown() throws Exception {
        Harness h = new Harness();
        h.physical.x2 = true;
        h.real.xButton(false, true, true);
        Frame first = h.frame();
        check("shift+mouse5 held: first frame", "4+", describe(first.events));
        Frame second = h.frame();
        check("shift+mouse5 held: nothing new", "", describe(second.events));
        check("shift+mouse5 held: state stays", "b3=0 b4=1", polled(second));
    }

    private static void withoutPhysicalState() throws Exception {
        Harness h = new Harness();
        h.physical.available = false;

        h.real.xButton(false, true, true);
        Frame held = h.frame();
        check("no physical state: held mouse5 stays mouse5", "4+", describe(held.events));

        Harness click = new Harness();
        click.physical.available = false;
        click.real.xButton(true, true, true);
        click.real.xButton(true, false, true);
        check("no physical state: completed mouse4 click is repaired", "3+ 3-", describe(click.frame().events));
    }

    private static void forwarding() throws Exception {
        Harness h = new Harness();
        check("forwards getButtonCount", Integer.valueOf(5), Integer.valueOf(h.wrapped.getButtonCount()));
        check("forwards hasWheel", Boolean.TRUE, Boolean.valueOf(h.wrapped.hasWheel()));
        check("forwards getWidth", Integer.valueOf(854), Integer.valueOf(h.wrapped.getWidth()));
    }

    private static String polled(Frame frame) {
        return "b3=" + frame.button3 + " b4=" + frame.button4;
    }

    private static String describe(List<int[]> events) {
        StringBuilder text = new StringBuilder();
        for (int[] event : events) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(event[0]).append(event[1] != 0 ? '+' : '-');
        }
        return text.toString();
    }

    private static void check(String name, Object expected, Object actual) {
        if (expected.equals(actual)) {
            System.out.println("PASS  " + name);
        } else {
            failures++;
            System.out.println("FAIL  " + name + " -> expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static final class Frame {
        final List<int[]> events;
        final int button3;
        final int button4;

        Frame(List<int[]> events, int button3, int button4) {
            this.events = events;
            this.button3 = button3;
            this.button4 = button4;
        }
    }

    private static final class Harness {
        final FakeInputImplementation real = new FakeInputImplementation();
        final FakePhysical physical = new FakePhysical();
        final InputImplementation wrapped;
        final IntBuffer coords = ByteBuffer.allocateDirect(3 * 4).asIntBuffer();

        Harness() {
            this.wrapped = (InputImplementation) Proxy.newProxyInstance(
                    InputImplementation.class.getClassLoader(),
                    new Class<?>[]{InputImplementation.class},
                    new XButtonMouseWrapper(real, physical));
        }

        /** One frame: Mouse.poll() polls the buttons and then reads new events. */
        Frame frame() throws Exception {
            ByteBuffer buttons = ByteBuffer.allocate(5);
            wrapped.pollMouse(coords, buttons);

            ByteBuffer events = ByteBuffer.allocate(EVENT_SIZE * 64);
            wrapped.readMouse(events);

            return new Frame(FakeInputImplementation.events(events),
                    buttons.get(3) & 0xFF, buttons.get(4) & 0xFF);
        }
    }

    private static final class FakePhysical implements PhysicalState {
        boolean available = true;
        boolean x1;
        boolean x2;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public boolean xButton1Down() {
            return x1;
        }

        @Override
        public boolean xButton2Down() {
            return x2;
        }
    }
}
