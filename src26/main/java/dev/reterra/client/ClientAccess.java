package dev.reterra.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Screen and input changes between the stable 26.x releases. */
public final class ClientAccess {
  private ClientAccess() {}
  public static Screen screen(Minecraft client) {
    //? if >=26.2 {
    /*return client.gui.screen();*/
    //? } else {
    return client.screen;
    //? }
  }
  public static void screen(Minecraft client, Screen screen) {
    //? if >=26.2 {
    /*client.gui.setScreen(screen);*/
    //? } else {
    client.setScreen(screen);
    //? }
  }
  public static boolean hidden(Minecraft client) {
    //? if >=26.2 {
    /*return client.gui.hud.isHidden();*/
    //? } else {
    return client.options.hideGui;
    //? }
  }
  public static net.minecraft.client.KeyMapping guideKey() {
    return new net.minecraft.client.KeyMapping("key.reterra.guide",
        com.mojang.blaze3d.platform.InputConstants.KEY_J,
        net.minecraft.client.KeyMapping.Category.register(dev.reterra.Content.id("reterra")));
  }
}
