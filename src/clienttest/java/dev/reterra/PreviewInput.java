package dev.reterra;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

/** Uses the same left-click input for preview interactions across Minecraft versions. */
final class PreviewInput {
  private PreviewInput() {}

  static void press(ButtonWidget button) {
    //? if <1.21.9 {
    button.onPress();
    //? } else {
    /*button.onPress(new net.minecraft.client.input.MouseInput(0, 0));
    *///? }
  }

  static void click(Screen screen, double x, double y) {
    //? if <1.21.9 {
    screen.mouseClicked(x, y, 0);
    //? } else {
    /*screen.mouseClicked(new net.minecraft.client.gui.Click(x, y,
        new net.minecraft.client.input.MouseInput(0, 0)), false);
    *///? }
  }
}
