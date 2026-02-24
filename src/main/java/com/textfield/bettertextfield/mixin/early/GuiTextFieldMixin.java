package com.textfield.bettertextfield.mixin.early;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.textfield.bettertextfield.ClientUtils;
import com.textfield.bettertextfield.TextFieldState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.LinkedList;

@Mixin(GuiTextField.class)
public abstract class GuiTextFieldMixin extends Gui {
    @Final
    @Shadow
    @Nullable // 原版处理了 null
    private FontRenderer fontRenderer;
    @Shadow
    private int lineScrollOffset;
    @Shadow
    private int selectionEnd;
    @Shadow
    private boolean isEnabled;

    /*
    拖选实现
     */

    @Unique
    private boolean betterTextField$isDragging = false;
    @Unique
    private long betterTextField$lastScrollTime = 0L;

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void injectDrag(int mouseX, int mouseY, int mouseButton, CallbackInfoReturnable<Boolean> cir) {
        this.betterTextField$isDragging = cir.getReturnValue() && mouseButton == 0;
    }

    @Inject(method = "drawTextBox", at = @At("HEAD"))
    private void onDrawTextBox(CallbackInfo ci) {
        GuiTextField self = (GuiTextField) (Object) this;
        if (this.betterTextField$isDragging && self.getVisible() && self.isFocused()) {
            if (!Mouse.isButtonDown(0)) {
                this.betterTextField$isDragging = false;
                return;
            }
            if (this.fontRenderer == null) return;
            Minecraft mc = Minecraft.getMinecraft();
            ScaledResolution sr = new ScaledResolution(mc);
            int mouseX = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
            int textX = self.x + (self.getEnableBackgroundDrawing() ? 4 : 0);
            int textWidth = self.getWidth();
            String fullText = self.getText();

            // 拖拽平移
            int offset = 0;
            if (mouseX < textX) {
                offset = -1;
            } else if (mouseX > textX + textWidth && this.fontRenderer.getStringWidth(fullText.substring(this.lineScrollOffset)) > textWidth) {
                offset = 1;
            }
            if (offset != 0) {
                long now = Minecraft.getSystemTime();
                if (now - this.betterTextField$lastScrollTime > 40L) {
                    this.lineScrollOffset += offset;
                    this.lineScrollOffset = MathHelper.clamp(this.lineScrollOffset, 0, fullText.length());
                    this.betterTextField$lastScrollTime = now;
                }
            }

            // 拖选逻辑
            // 截取从 lineScrollOffset 开始，且宽度不超过总宽度的子串，也就是当前可见的字符
            String trimmed = this.fontRenderer.trimStringToWidth(fullText.substring(this.lineScrollOffset), textWidth);
            // 截取鼠标到左边缘的字符
            trimmed = this.fontRenderer.trimStringToWidth(trimmed, Math.max(0, mouseX - textX));
            // 定下另一个锚点，第一个锚点是 cursorPosition，两点确定选区
            this.selectionEnd = trimmed.length() + this.lineScrollOffset;
        }
    }

    @Inject(method = "setFocused", at = @At("RETURN"))
    private void cleanUpSelection(boolean focused, CallbackInfo ci) {
        if (!focused) {
            GuiTextField self = (GuiTextField) (Object) this;
            self.moveCursorBy(0); // 取消选中
        }
    }

    /*
    撤销/重做实现
     */

    @Unique
    private static final int MAX_HISTORY_SIZE = 50;

    @Unique
    private final LinkedList<TextFieldState> betterTextField$undoStack = new LinkedList<>();
    @Unique
    private final LinkedList<TextFieldState> betterTextField$redoStack = new LinkedList<>();
    @Unique
    private boolean betterTextField$isRestoring = false;

    @Unique
    private void betterTextField$saveHistory() {
        if (this.betterTextField$isRestoring) return;
        if (this.betterTextField$undoStack.size() >= MAX_HISTORY_SIZE) {
            this.betterTextField$undoStack.removeFirst();
        }
        GuiTextField self = (GuiTextField) (Object) this;
        this.betterTextField$undoStack.addLast(new TextFieldState(self));
        this.betterTextField$redoStack.clear();
    }

    @Unique
    private void betterTextField$performUndo() {
        if (!this.betterTextField$undoStack.isEmpty()) {
            this.betterTextField$isRestoring = true;
            GuiTextField self = (GuiTextField) (Object) this;
            this.betterTextField$redoStack.addLast(new TextFieldState(self));
            TextFieldState previousState = this.betterTextField$undoStack.removeLast();
            previousState.writeTo(self);
            this.betterTextField$isRestoring = false;
        }
    }

    @Unique
    private void betterTextField$performRedo() {
        if (!this.betterTextField$redoStack.isEmpty()) {
            this.betterTextField$isRestoring = true;
            GuiTextField self = (GuiTextField) (Object) this;
            this.betterTextField$undoStack.addLast(new TextFieldState(self));
            TextFieldState nextState = this.betterTextField$redoStack.removeLast();
            nextState.writeTo(self);
            this.betterTextField$isRestoring = false;
        }
    }

    @Inject(method = "writeText", at = @At("HEAD"))
    private void onWriteText(String textToWrite, CallbackInfo ci) {
        this.betterTextField$saveHistory();
    }

    @Inject(method = "deleteFromCursor", at = @At("HEAD"))
    private void onDeleteFromCursor(int num, CallbackInfo ci) {
        this.betterTextField$saveHistory();
    }

    @Inject(method = "deleteWords", at = @At("HEAD"))
    private void onDeleteWords(int num, CallbackInfo ci) {
        this.betterTextField$saveHistory();
    }

    @Inject(method = "textboxKeyTyped", at = @At("HEAD"), cancellable = true)
    private void injectUndoAndRedoStack(char typedChar, int keyCode, CallbackInfoReturnable<Boolean> cir) {
        GuiTextField self = (GuiTextField) (Object) this;
        if (!self.isFocused() || !this.isEnabled) return;
        if (ClientUtils.isKeyComboCtrlZ(keyCode)) {
            this.betterTextField$performUndo();
            cir.cancel();
        } else if (ClientUtils.isKeyComboCtrlY(keyCode)) {
            this.betterTextField$performRedo();
            cir.cancel();
        }
    }

    /*
    补全原版缺失的 respond
     */

    @WrapMethod(method = "setText")
    private void addSetTextRespond(String textIn, Operation<Void> original) {
        GuiTextField self = (GuiTextField) (Object) this;
        String previousText = self.getText();
        original.call(textIn);
        if (!self.getText().equals(previousText)) {
            self.setResponderEntryValue(self.getId(), self.getText());
        }
    }

    @WrapMethod(method = "setMaxStringLength")
    private void addTrimRespond(int length, Operation<Void> original) {
        GuiTextField self = (GuiTextField) (Object) this;
        String previousText = self.getText();
        original.call(length);
        if (!self.getText().equals(previousText)) {
            self.setResponderEntryValue(self.getId(), self.getText());
        }
    }

    /*
    选中高亮
     */

    @ModifyArg(
            method = "drawTextBox",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiTextField;drawRect(IIIII)V",
                    ordinal = 0
            ),
            index = 4
    )
    private int modifyBoarderColor(int originalColor) {
        GuiTextField self = (GuiTextField) (Object) this;
        return self.isFocused() ? 0xFFFFFFFF : originalColor;
    }
}
