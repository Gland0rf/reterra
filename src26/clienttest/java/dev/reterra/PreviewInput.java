package dev.reterra;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

/** Uses the same left-click input for preview interactions across Minecraft versions. */
final class PreviewInput {
  private PreviewInput() {}

  static void press(Button button) {
    button.onPress(new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0));
  }

  static void click(Screen screen, double x, double y) {
    screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x, y,
        new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
  }
}
