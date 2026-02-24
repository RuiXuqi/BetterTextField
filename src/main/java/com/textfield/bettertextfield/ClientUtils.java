package com.textfield.bettertextfield;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

public final class ClientUtils {
    public static boolean isKeyComboCtrlZ(int key) {
        return key == Keyboard.KEY_Z && isCtrlCombo();
    }

    public static boolean isKeyComboCtrlY(int key) {
        return key == Keyboard.KEY_Y && isCtrlCombo();
    }

    public static boolean isCtrlCombo() {
        return GuiScreen.isCtrlKeyDown() && !GuiScreen.isShiftKeyDown() && !GuiScreen.isAltKeyDown();
    }
}
