package dev.reterra.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Shared stone-and-paper styling, with ordinary Minecraft button and focus behavior. */
public final class FieldUi {
  public static final int INK = 0xFF30392F, MUTED = 0xFF616957, GREEN = 0xFF3F693D;
  public static final int STONE = 0xFFC6C7BB, PAPER = 0xFFE4DFC6, RECESS = 0xFFB5B8AB;
  public static final int BORDER = 0xFF4D554B, COPPER = 0xFF946B45;

  private FieldUi() {}

  public static void push(DrawContext c, float x, float y, float scale) {
    //? if <1.21.6 {
    c.getMatrices().push();
    c.getMatrices().translate(x, y, 0);
    c.getMatrices().scale(scale, scale, 1);
    //? } else {
    /*c.getMatrices().pushMatrix();
    c.getMatrices().translate(x, y);
    c.getMatrices().scale(scale, scale);
    *///? }
  }

  public static void pop(DrawContext c) {
    //? if <1.21.6 {
    c.getMatrices().pop();
    //? } else {
    /*c.getMatrices().popMatrix();
    *///? }
  }

  public static void wrapped(
      DrawContext c, net.minecraft.client.font.TextRenderer font,
      net.minecraft.text.StringVisitable text, int x, int y, int width, int color) {
    for (var line : font.wrapLines(text, width)) {
      c.drawText(font, line, x, y, color, false);
      y += font.fontHeight;
    }
  }

  public static void panel(DrawContext c, int x, int y, int width, int height, boolean paper) {
    c.fill(x, y, x + width, y + height, BORDER);
    c.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFEEEEDE);
    c.fill(x + 3, y + 3, x + width - 2, y + height - 2, paper ? PAPER : STONE);
    c.fill(x + width - 2, y + 2, x + width - 1, y + height - 1, 0xFF858D7C);
    c.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, 0xFF858D7C);
  }

  public static void slot(DrawContext c, int x, int y) {
    c.fill(x - 1, y - 1, x + 17, y + 17, 0xFFF1F0DF);
    c.fill(x - 1, y - 1, x + 16, y + 16, 0xFF72796B);
    c.fill(x, y, x + 16, y + 16, 0xFF9B9F91);
  }

  public static void check(DrawContext c, int x, int y, boolean done) {
    c.fill(x, y, x + 8, y + 8, done ? GREEN : 0xFF9B9F89);
    c.fill(x + 1, y + 1, x + 7, y + 7, done ? 0xFF719354 : PAPER);
    if (done) {
      c.fill(x + 2, y + 4, x + 3, y + 6, 0xFFF5EFDA);
      c.fill(x + 3, y + 5, x + 4, y + 7, 0xFFF5EFDA);
      c.fill(x + 4, y + 3, x + 5, y + 6, 0xFFF5EFDA);
      c.fill(x + 5, y + 2, x + 6, y + 4, 0xFFF5EFDA);
    }
  }

  public static Button button(
      net.minecraft.text.Text text, ButtonWidget.PressAction action, int x, int y, int width, int height) {
    return new Button(x, y, width, height, text, action);
  }

  public static final class Button extends ButtonWidget {
    private boolean selected;

    private Button(int x, int y, int width, int height, net.minecraft.text.Text text, PressAction action) {
      super(x, y, width, height, text, action, DEFAULT_NARRATION_SUPPLIER);
    }

    public void selected(boolean value) {
      selected = value;
    }

    //? if >=1.21.11 {
    /*@Override
    protected void drawLabel(net.minecraft.client.font.DrawnTextConsumer consumer) {}
    *///? }

    @Override
    //? if <1.21.11 {
    protected void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
    //? } else {
    /*protected void drawIcon(DrawContext c, int mouseX, int mouseY, float delta) {
    *///? }
      int x = getX(), y = getY(), width = getWidth(), height = getHeight();
      boolean hover = isHovered() || isFocused();
      c.fill(x, y, x + width, y + height, hover && active ? COPPER : BORDER);
      c.fill(
          x + 1,
          y + 1,
          x + width - 1,
          y + height - 1,
          !active ? 0xFFB2B6A9 : selected ? 0xFFBCCCA5 : hover ? 0xFFE6E6D4 : 0xFFD3D5C7);
      c.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFFF2F1DF);
      if (selected) c.fill(x + 2, y + height - 3, x + width - 2, y + height - 1, GREEN);
      var font = MinecraftClient.getInstance().textRenderer;
      String label = font.trimToWidth(getMessage().getString(), width - 8);
      c.drawText(
          font,
          label,
          x + (width - font.getWidth(label)) / 2,
          y + (height - 8) / 2,
          active ? INK : 0xFF727969,
          false);
    }
  }
}
