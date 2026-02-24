package net.minecraft.client.gui;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

@SuppressWarnings({"ManualMinMaxCalculation", "unused"})
@SideOnly(Side.CLIENT)
public class GuiTextFieldComment extends Gui {
    private final int id;
    private final FontRenderer fontRenderer;
    public int x;
    public int y;
    /// the FULL width of text field
    public int width;
    public int height;
    /// full text in the box regardless displayed or not
    private String text = "";
    /// max length. if text longer than this, type will fail
    private int maxStringLength = 32;
    private int cursorCounter;
    /// the black bg and gray outline. will also make the field larger
    private boolean enableBackgroundDrawing = true;
    /// if true the textbox can lose focus by clicking elsewhere on the screen
    private boolean canLoseFocus = true;
    /// If this value is true along with isEnabled, keyTyped will process the keys.
    private boolean isFocused;
    /// If this value is true along with isFocused, keyTyped will process the keys.
    private boolean isEnabled = true;
    /// current character index that should be used as start of the rendered text.
    private int lineScrollOffset;
    private int cursorPosition;
    /// other selection position, maybe the same as the cursor
    private int selectionEnd;
    private int enabledColor = 0xE0E0E0; // Light Gray
    private int disabledColor = 0x707070; // Dark Gray
    private boolean visible = true;
    /// call the screen when text is changed<br>
    /// {@link #setText(String)} & {@link #setMaxStringLength(int)} is missing in 1.12.2 and fixed in modern versions
    private GuiPageButtonList.GuiResponder guiResponder;
    private Predicate<String> validator = Predicates.alwaysTrue();

    /**
     * @param id only for {@link #guiResponder} and have nothing to do with {@link GuiButton#id}.
     */
    public GuiTextFieldComment(int id, FontRenderer fontRenderer, int x, int y, int width, int height) {
        this.id = id;
        this.fontRenderer = fontRenderer;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Sets the text of the textbox, and moves the cursor to the end.
     */
    public void setText(String text) {
        if (this.validator.apply(text)) {
            if (text.length() > this.maxStringLength) {
                this.text = text.substring(0, this.maxStringLength);
            } else {
                this.text = text;
            }

            this.setCursorPositionEnd();
        }
    }

    /**
     * returns the text between the cursor and selectionEnd
     */
    public String getSelectedText() {
        int start = this.cursorPosition < this.selectionEnd ? this.cursorPosition : this.selectionEnd;
        int end = this.cursorPosition < this.selectionEnd ? this.selectionEnd : this.cursorPosition;
        return this.text.substring(start, end);
    }

    public void setValidator(Predicate<String> validator) {
        this.validator = validator;
    }

    /**
     * Adds the given text after the cursor, or replaces the currently selected text if there is a selection.
     */
    public void writeText(String textToWrite) {
        String newText = "";
        String filteredText = ChatAllowedCharacters.filterAllowedCharacters(textToWrite);
        int selectionStart = this.cursorPosition < this.selectionEnd ? this.cursorPosition : this.selectionEnd;
        int selectionEnd = this.cursorPosition < this.selectionEnd ? this.selectionEnd : this.cursorPosition;
        int availableSpace = this.maxStringLength - this.text.length() - (selectionStart - selectionEnd);

        if (!this.text.isEmpty()) {
            newText = newText + this.text.substring(0, selectionStart);
        }

        int insertedLength;

        if (availableSpace < filteredText.length()) {
            newText = newText + filteredText.substring(0, availableSpace);
            insertedLength = availableSpace;
        } else {
            newText = newText + filteredText;
            insertedLength = filteredText.length();
        }

        if (!this.text.isEmpty() && selectionEnd < this.text.length()) {
            newText = newText + this.text.substring(selectionEnd);
        }

        if (this.validator.apply(newText)) {
            this.text = newText;
            this.moveCursorBy(selectionStart - this.selectionEnd + insertedLength);
            this.setResponderEntryValue(this.id, this.text);
        }
    }

    /**
     * Notifies {@link #guiResponder} that the text has changed.
     *
     * @param id used to judge the box, so the screens can have only one responder
     * @param text current text in the box
     */
    public void setResponderEntryValue(int id, String text) {
        if (this.guiResponder != null) {
            this.guiResponder.setEntryValue(id, text);
        }
    }

    /**
     * Deletes the given number of words from the current cursor's position, unless there is currently a selection, in
     * which case the selection is deleted instead.
     */
    public void deleteWords(int num) {
        if (!this.text.isEmpty()) {
            if (this.selectionEnd != this.cursorPosition) {
                this.writeText("");
            } else {
                this.deleteFromCursor(this.getNthWordFromCursor(num) - this.cursorPosition);
            }
        }
    }

    /**
     * Deletes the given number of characters from the current cursor's position, unless there is currently a selection,
     * in which case the selection is deleted instead.
     */
    public void deleteFromCursor(int num) {
        if (!this.text.isEmpty()) {
            if (this.selectionEnd != this.cursorPosition) {
                this.writeText("");
            } else {
                boolean isBackwards = num < 0;
                int startIndex = isBackwards ? this.cursorPosition + num : this.cursorPosition;
                int endIndex = isBackwards ? this.cursorPosition : this.cursorPosition + num;
                String s = "";

                if (startIndex >= 0) {
                    s = this.text.substring(0, startIndex);
                }

                if (endIndex < this.text.length()) {
                    s = s + this.text.substring(endIndex);
                }

                if (this.validator.apply(s)) {
                    this.text = s;

                    if (isBackwards) {
                        this.moveCursorBy(num);
                    }

                    this.setResponderEntryValue(this.id, this.text);
                }
            }
        }
    }

    /**
     * Gets the starting index of the word at the specified number of words away from the cursor position.
     */
    public int getNthWordFromCursor(int numWords) {
        return this.getNthWordFromPos(numWords, this.getCursorPosition());
    }

    /**
     * Gets the starting index of the word at a distance of the specified number of words away from the given position.
     */
    public int getNthWordFromPos(int n, int pos) {
        return this.getNthWordFromPosWS(n, pos, true);
    }

    /**
     * Like getNthWordFromPos (which wraps this), but adds option for skipping consecutive spaces
     */
    public int getNthWordFromPosWS(int n, int pos, boolean skipWs) {
        int currentPos = pos;
        boolean isBackwards = n < 0;
        int steps = Math.abs(n);

        for (int step = 0; step < steps; ++step) {
            if (!isBackwards) {
                int len = this.text.length();
                currentPos = this.text.indexOf(32, currentPos); // 32 is Space

                if (currentPos == -1) {
                    currentPos = len;
                } else {
                    while (skipWs && currentPos < len && this.text.charAt(currentPos) == ' ') {
                        ++currentPos;
                    }
                }
            } else {
                while (skipWs && currentPos > 0 && this.text.charAt(currentPos - 1) == ' ') {
                    --currentPos;
                }

                while (currentPos > 0 && this.text.charAt(currentPos - 1) != ' ') {
                    --currentPos;
                }
            }
        }

        return currentPos;
    }

    /**
     * Moves the text cursor by a specified number of characters and clears the selection
     */
    public void moveCursorBy(int num) {
        this.setCursorPosition(this.selectionEnd + num);
    }

    /**
     * Sets the current position of the cursor.
     */
    public void setCursorPosition(int pos) {
        this.cursorPosition = pos;
        int len = this.text.length();
        this.cursorPosition = MathHelper.clamp(this.cursorPosition, 0, len);
        this.setSelectionPos(this.cursorPosition);
    }

    /**
     * Moves the cursor to the very start of this text box.
     */
    public void setCursorPositionZero() {
        this.setCursorPosition(0);
    }

    /**
     * Moves the cursor to the very end of this text box.
     */
    public void setCursorPositionEnd() {
        this.setCursorPosition(this.text.length());
    }

    /**
     * Call this method from your GuiScreen to process the keys into the textbox
     */
    public boolean textboxKeyTyped(char typedChar, int keyCode) {
        if (!this.isFocused) {
            return false;
        } else if (GuiScreen.isKeyComboCtrlA(keyCode)) {
            this.setCursorPositionEnd();
            this.setSelectionPos(0);
            return true;
        } else if (GuiScreen.isKeyComboCtrlC(keyCode)) {
            GuiScreen.setClipboardString(this.getSelectedText());
            return true;
        } else if (GuiScreen.isKeyComboCtrlV(keyCode)) {
            if (this.isEnabled) {
                this.writeText(GuiScreen.getClipboardString());
            }

            return true;
        } else if (GuiScreen.isKeyComboCtrlX(keyCode)) {
            GuiScreen.setClipboardString(this.getSelectedText());

            if (this.isEnabled) {
                this.writeText("");
            }

            return true;
        } else {
            switch (keyCode) {
                case Keyboard.KEY_BACK:

                    if (GuiScreen.isCtrlKeyDown()) {
                        if (this.isEnabled) {
                            this.deleteWords(-1);
                        }
                    } else if (this.isEnabled) {
                        this.deleteFromCursor(-1);
                    }

                    return true;
                case Keyboard.KEY_HOME:

                    if (GuiScreen.isShiftKeyDown()) {
                        this.setSelectionPos(0);
                    } else {
                        this.setCursorPositionZero();
                    }

                    return true;
                case Keyboard.KEY_LEFT:

                    if (GuiScreen.isShiftKeyDown()) {
                        if (GuiScreen.isCtrlKeyDown()) {
                            this.setSelectionPos(this.getNthWordFromPos(-1, this.getSelectionEnd()));
                        } else {
                            this.setSelectionPos(this.getSelectionEnd() - 1);
                        }
                    } else if (GuiScreen.isCtrlKeyDown()) {
                        this.setCursorPosition(this.getNthWordFromCursor(-1));
                    } else {
                        this.moveCursorBy(-1);
                    }

                    return true;
                case Keyboard.KEY_RIGHT:

                    if (GuiScreen.isShiftKeyDown()) {
                        if (GuiScreen.isCtrlKeyDown()) {
                            this.setSelectionPos(this.getNthWordFromPos(1, this.getSelectionEnd()));
                        } else {
                            this.setSelectionPos(this.getSelectionEnd() + 1);
                        }
                    } else if (GuiScreen.isCtrlKeyDown()) {
                        this.setCursorPosition(this.getNthWordFromCursor(1));
                    } else {
                        this.moveCursorBy(1);
                    }

                    return true;
                case Keyboard.KEY_END:

                    if (GuiScreen.isShiftKeyDown()) {
                        this.setSelectionPos(this.text.length());
                    } else {
                        this.setCursorPositionEnd();
                    }

                    return true;
                case Keyboard.KEY_DELETE:

                    if (GuiScreen.isCtrlKeyDown()) {
                        if (this.isEnabled) {
                            this.deleteWords(1);
                        }
                    } else if (this.isEnabled) {
                        this.deleteFromCursor(1);
                    }

                    return true;
                default:

                    if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
                        if (this.isEnabled) {
                            this.writeText(Character.toString(typedChar));
                        }

                        return true;
                    } else {
                        return false;
                    }
            }
        }
    }

    /**
     * Called when mouse is clicked, regardless as to whether it is over this button or not.
     */
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        boolean isHovered = mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;

        if (this.canLoseFocus) {
            this.setFocused(isHovered);
        }

        if (this.isFocused && isHovered && mouseButton == 0) {
            int clickX = mouseX - this.x;

            if (this.enableBackgroundDrawing) {
                clickX -= 4;
            }

            String s = this.fontRenderer.trimStringToWidth(this.text.substring(this.lineScrollOffset), this.getWidth());
            this.setCursorPosition(this.fontRenderer.trimStringToWidth(s, clickX).length() + this.lineScrollOffset);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Draws the textbox
     */
    @SuppressWarnings("UnusedAssignment")
    public void drawTextBox() {
        if (this.getVisible()) {
            if (this.getEnableBackgroundDrawing()) {
                // Border Color
                drawRect(this.x - 1, this.y - 1, this.x + this.width + 1, this.y + this.height + 1, 0xFFA0A0A0);
                // Black Background
                drawRect(this.x, this.y, this.x + this.width, this.y + this.height, 0xFF000000);
            }

            int color = this.isEnabled ? this.enabledColor : this.disabledColor;
            int cursorOffset = this.cursorPosition - this.lineScrollOffset;
            int selectionOffset = this.selectionEnd - this.lineScrollOffset;
            String visibleText = this.fontRenderer.trimStringToWidth(this.text.substring(this.lineScrollOffset), this.getWidth());
            boolean cursorInView = cursorOffset >= 0 && cursorOffset <= visibleText.length();
            boolean shouldCursorBlink = this.isFocused && this.cursorCounter / 6 % 2 == 0 && cursorInView;
            int boxStartX = this.enableBackgroundDrawing ? this.x + 4 : this.x;
            int boxStartY = this.enableBackgroundDrawing ? this.y + (this.height - 8) / 2 : this.y;
            int textRenderX = boxStartX;

            if (selectionOffset > visibleText.length()) {
                selectionOffset = visibleText.length();
            }

            if (!visibleText.isEmpty()) {
                String textBeforeCursor = cursorInView ? visibleText.substring(0, cursorOffset) : visibleText;
                textRenderX = this.fontRenderer.drawStringWithShadow(textBeforeCursor, (float) boxStartX, (float) boxStartY, color);
            }

            boolean isCursorAtEnd = this.cursorPosition < this.text.length() || this.text.length() >= this.getMaxStringLength();
            int cursorRenderX = textRenderX;

            if (!cursorInView) {
                cursorRenderX = cursorOffset > 0 ? boxStartX + this.width : boxStartX;
            } else if (isCursorAtEnd) {
                cursorRenderX = textRenderX - 1;
                --textRenderX;
            }

            if (!visibleText.isEmpty() && cursorInView && cursorOffset < visibleText.length()) {
                textRenderX = this.fontRenderer.drawStringWithShadow(visibleText.substring(cursorOffset), (float) textRenderX, (float) boxStartY, color);
            }

            if (shouldCursorBlink) {
                if (isCursorAtEnd) {
                    Gui.drawRect(cursorRenderX, boxStartY - 1, cursorRenderX + 1, boxStartY + 1 + this.fontRenderer.FONT_HEIGHT, 0xFFD0D0D0);
                } else {
                    this.fontRenderer.drawStringWithShadow("_", (float) cursorRenderX, (float) boxStartY, color);
                }
            }

            if (selectionOffset != cursorOffset) {
                int selectionRenderX = boxStartX + this.fontRenderer.getStringWidth(visibleText.substring(0, selectionOffset));
                this.drawSelectionBox(cursorRenderX, boxStartY - 1, selectionRenderX - 1, boxStartY + 1 + this.fontRenderer.FONT_HEIGHT);
            }
        }
    }

    /**
     * Draws the blue selection box.
     */
    private void drawSelectionBox(int startX, int startY, int endX, int endY) {
        if (startX < endX) {
            int temp = startX;
            startX = endX;
            endX = temp;
        }

        if (startY < endY) {
            int temp = startY;
            startY = endY;
            endY = temp;
        }

        if (endX > this.x + this.width) {
            endX = this.x + this.width;
        }

        if (startX > this.x + this.width) {
            startX = this.x + this.width;
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        GlStateManager.color(0.0F, 0.0F, 255.0F, 255.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.enableColorLogic();
        GlStateManager.colorLogicOp(GlStateManager.LogicOp.OR_REVERSE);
        bufferbuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        bufferbuilder.pos(startX, endY, 0.0D).endVertex();
        bufferbuilder.pos(endX, endY, 0.0D).endVertex();
        bufferbuilder.pos(endX, startY, 0.0D).endVertex();
        bufferbuilder.pos(startX, startY, 0.0D).endVertex();
        tessellator.draw();
        GlStateManager.disableColorLogic();
        GlStateManager.enableTexture2D();
    }

    /**
     * Sets focus to this gui element
     */
    public void setFocused(boolean isFocused) {
        if (isFocused && !this.isFocused) {
            this.cursorCounter = 0;
        }

        this.isFocused = isFocused;

        if (Minecraft.getMinecraft().currentScreen != null) {
            Minecraft.getMinecraft().currentScreen.setFocused(isFocused);
        }
    }

    /**
     * Sets the position of the selection anchor (the selection anchor and the cursor position mark the edges of the
     * selection). If the anchor is set beyond the bounds of the current text, it will be put back inside.
     */
    public void setSelectionPos(int position) {
        // 当前文本的长度
        int textLen = this.text.length();

        // 确保位置不大于文本长度
        if (position > textLen) {
            position = textLen;
        }
        // 确保位置不小于 0
        if (position < 0) {
            position = 0;
        }

        // 更新选择锚点位置
        this.selectionEnd = position;

        // 滚动视窗计算
        if (this.fontRenderer != null) {
            // 如果文本被删除导致滚动偏移量超过了现有的文本长度，将其重置到末尾
            if (this.lineScrollOffset > textLen) {
                this.lineScrollOffset = textLen;
            }

            int availableWidth = this.getWidth();
            // 计算从当前滚动位置开始，能显示多少文本
            // trimStringToWidth 会返回 text 从 lineScrollOffset 开始，且宽度不超过 availableWidth 的子串
            String trimmed = this.fontRenderer.trimStringToWidth(this.text.substring(this.lineScrollOffset), availableWidth);
            // 计算当前可视区域的结束索引 (起始偏移 + 可视文本长度)
            int posInTrimmed = trimmed.length() + this.lineScrollOffset;

            // 如果目标位置正好是当前行的起始位置，这通常意味着在向左移动或删除了内容。
            // 尝试将视窗向左移动一段距离，以便用户能看到前面的上下文。
            if (position == this.lineScrollOffset) {
                this.lineScrollOffset -= this.fontRenderer.trimStringToWidth(this.text, availableWidth, true).length();
            }

            // 如果目标位置超过了可视区域的右边界，向右滚动
            if (position > posInTrimmed) {
                // 增加滚动偏移量，让目标位置刚好出现在可视区域的最右侧
                this.lineScrollOffset += position - posInTrimmed;
            }
            // 如果目标位置在可视区域的左边界之前 (或相等)，向左滚动
            else if (position <= this.lineScrollOffset) {
                // 减少滚动偏移量，直接把滚动起始点设置为目标位置
                this.lineScrollOffset -= this.lineScrollOffset - position;
            }

            // 确保计算后的滚动偏移量在有效范围内 [0, textLen]
            this.lineScrollOffset = MathHelper.clamp(this.lineScrollOffset, 0, textLen);
        }
    }

    public void updateCursorCounter() {
        ++this.cursorCounter;
    }

    /**
     * 内部文字的宽度。
     */
    public int getWidth() {
        return this.getEnableBackgroundDrawing() ? this.width - 8 : this.width;
    }

    public void setMaxStringLength(int length) {
        this.maxStringLength = length;

        if (this.text.length() > length) {
            this.text = this.text.substring(0, length);
        }
    }

    /*
    Boring getters and setters
     */

    public String getText() {
        return this.text;
    }

    public int getId() {
        return this.id;
    }

    public boolean isFocused() {
        return this.isFocused;
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }

    public int getSelectionEnd() {
        return this.selectionEnd;
    }

    public void setGuiResponder(GuiPageButtonList.GuiResponder guiResponder) {
        this.guiResponder = guiResponder;
    }

    public void setCanLoseFocus(boolean canLoseFocus) {
        this.canLoseFocus = canLoseFocus;
    }

    public boolean getVisible() {
        return this.visible;
    }

    public void setVisible(boolean isVisible) {
        this.visible = isVisible;
    }

    public int getMaxStringLength() {
        return this.maxStringLength;
    }

    public int getCursorPosition() {
        return this.cursorPosition;
    }

    public boolean getEnableBackgroundDrawing() {
        return this.enableBackgroundDrawing;
    }

    public void setEnableBackgroundDrawing(boolean enableBackgroundDrawing) {
        this.enableBackgroundDrawing = enableBackgroundDrawing;
    }

    public void setTextColor(int color) {
        this.enabledColor = color;
    }

    public void setDisabledTextColour(int color) {
        this.disabledColor = color;
    }
}
