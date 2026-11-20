package com.mousexbuttonfix;

/**
 * Small manual check: prints every change of the two side buttons for a few seconds.
 * Use it to confirm that the hardware state is readable on this machine.
 *
 * <pre>java -cp ... com.mousexbuttonfix.PhysicalStateProbe 15</pre>
 */
public final class PhysicalStateProbe {

    public static void main(String[] args) throws Exception {
        int seconds = args.length > 0 ? Integer.parseInt(args[0]) : 15;
        if (!JnaPhysicalState.INSTANCE.available()) {
            System.out.println("Physical state is NOT available on this machine.");
            return;
        }
        System.out.println("Press and release Mouse4 and Mouse5 for " + seconds + " seconds...");
        long until = System.currentTimeMillis() + seconds * 1000L;
        boolean last1 = false;
        boolean last2 = false;
        while (System.currentTimeMillis() < until) {
            boolean x1 = JnaPhysicalState.INSTANCE.xButton1Down();
            boolean x2 = JnaPhysicalState.INSTANCE.xButton2Down();
            if (x1 != last1 || x2 != last2) {
                System.out.println("mouse4=" + x1 + " mouse5=" + x2);
                last1 = x1;
                last2 = x2;
            }
            Thread.sleep(10L);
        }
        System.out.println("Done.");
    }
}
