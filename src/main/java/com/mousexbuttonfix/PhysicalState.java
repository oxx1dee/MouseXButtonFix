package com.mousexbuttonfix;

/**
 * Source for the <em>real</em> state of the two side mouse buttons.
 *
 * <p>Needed because LWJGL gets the same buttons wrong in every place it reports them,
 * so its own state cannot be used to tell Mouse4 and Mouse5 apart.</p>
 */
interface PhysicalState {

    /** @return whether this source can currently report button state. */
    boolean available();

    /** @return whether the first side button (Mouse4 / XBUTTON1) is physically pressed. */
    boolean xButton1Down();

    /** @return whether the second side button (Mouse5 / XBUTTON2) is physically pressed. */
    boolean xButton2Down();
}
