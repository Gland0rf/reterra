package dev.reterra.client;

import dev.reterra.screen.MachineScreenHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ReTerraClient implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    MachineAnimations.initialize();
    HandledScreens.register(MachineScreenHandler.TYPE, MachineScreen::new);
    var guideKey =
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
            new net.minecraft.client.option.KeyBinding(
                "key.reterra.guide",
                net.minecraft.client.util.InputUtil.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_J,
                //? if <1.21.9 {
                "category.reterra"));
                //? } else {
                /*net.minecraft.client.option.KeyBinding.Category.create(dev.reterra.Content.id("reterra"))));
                *///? }
    net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          while (guideKey.wasPressed()) if (client.player != null) FieldGuideScreen.open(client);
        });
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
        dev.reterra.screen.GuidePayload.ID,
        (payload, context) ->
            context
                .client()
                .execute(
                    () -> {
                      FieldGuideScreen.update(payload);
                      FarmhouseCompass.update(payload.farmhouse());
                      if (payload.open()) FieldGuideScreen.open(context.client());
                    }));
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, client) -> {
          FieldGuideScreen.reset();
          FarmhouseCompass.update(null);
        });
    net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
        FarmhouseCompass::render);
    ItemTooltipCallback.EVENT.register(
        (stack, context, type, lines) -> {
          if (stack.isOf(Items.COARSE_DIRT) || stack.isOf(Items.ROOTED_DIRT))
            lines.add(Text.translatable("hint.reterra.sterile_soil").formatted(Formatting.GRAY));
          if (stack.isOf(Items.GLASS_BOTTLE))
            lines.add(Text.translatable("hint.reterra.empty_bottle").formatted(Formatting.GRAY));
          if (stack.getItem() instanceof net.minecraft.item.BlockItem b
              && dev.reterra.Content.isMachine(b.getBlock()))
            lines.add(Text.translatable("hint.reterra.machine").formatted(Formatting.GRAY));
        });
  }
}
