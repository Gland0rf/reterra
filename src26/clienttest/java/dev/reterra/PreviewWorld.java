package dev.reterra;

final class PreviewWorld {
  private PreviewWorld() {}

  static void time(net.minecraft.server.level.ServerLevel world, long ticks) {
    var clock = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_CLOCK)
        .getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD);
    world.clockManager().setTotalTicks(clock, ticks);
  }
  static void hide(net.minecraft.client.Minecraft client) {
    //? if >=26.3 {
    /*org.lwjgl.sdl.SDLVideo.SDL_HideWindow(client.getWindow().handle());*/
    //? } else {
    org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle());
    //? }
  }
  static boolean loading(net.minecraft.client.Minecraft client) {
    //? if >=26.2 {
    /*return client.gui.overlay() != null;*/
    //? } else {
    return client.getOverlay() != null;
    //? }
  }
  static void hideHud(net.minecraft.client.Minecraft client) {
    //? if >=26.2 {
    /*if (!client.gui.hud.isHidden()) client.gui.hud.toggle();*/
    //? } else {
    client.options.hideGui = true;
    //? }
  }
  static com.mojang.blaze3d.pipeline.RenderTarget renderTarget(net.minecraft.client.Minecraft client) {
    //? if >=26.2 {
    /*return client.gameRenderer.mainRenderTarget();*/
    //? } else {
    return client.getMainRenderTarget();
    //? }
  }
  static void dismissLoadPrompt(net.minecraft.client.Minecraft client) {
    if (client.level != null || loading(client)) return;
    var screen = dev.reterra.client.ClientAccess.screen(client);
    if (screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
      screen.onClose();
      return;
    }
    String label;
    if (screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
      // These worlds are disposable copies; the bundled seed stays untouched.
      label = net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinSkipButton").getString();
    } else if (screen instanceof net.minecraft.client.gui.screens.ConfirmScreen) {
      // The file-layout upgrade ends with a separate "Join now?" Yes/No dialog.
      boolean upgradeComplete = screen.getTitle().getString().equals(
          net.minecraft.network.chat.Component.translatable("upgradeWorld.done").getString());
      label = (upgradeComplete ? net.minecraft.network.chat.CommonComponents.GUI_YES
          : net.minecraft.network.chat.CommonComponents.GUI_PROCEED).getString();
    } else return;
    for (var child : screen.children()) {
      if (child instanceof net.minecraft.client.gui.components.Button button
          && button.active && button.visible
          && (button.getMessage().getString().equals(label)
              || (screen instanceof net.minecraft.client.gui.screens.ConfirmScreen
                  && button.getMessage().getString().equals(
                      net.minecraft.network.chat.Component.translatable("gui.continue").getString())))) {
        System.out.println("Preview world loading: " + button.getMessage().getString());
        PreviewInput.press(button);
        return;
      }
    }
  }
}
