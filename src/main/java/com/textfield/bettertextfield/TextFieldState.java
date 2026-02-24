package com.textfield.bettertextfield;

import com.github.bsideup.jabel.Desugar;
import net.minecraft.client.gui.GuiTextField;

@Desugar
public record TextFieldState(
        String text, int cursorPosition, int selectionEnd
) {
    public TextFieldState(GuiTextField textField) {
        this(textField.getText(), textField.getCursorPosition(), textField.getSelectionEnd());
    }

    public void writeTo(GuiTextField textField) {
        textField.setText(this.text);
        textField.setCursorPosition(this.cursorPosition);
        textField.setSelectionPos(this.selectionEnd);
    }
}
