package dev.reterra.client;

import dev.reterra.screen.MachineScreenHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

public final class ReTerraClient implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    MachineAnimations.initialize();
    MenuScreens.register(MachineScreenHandler.TYPE, MachineScreen::new);
    var guideKey =
        net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(
            ClientAccess.guideKey());
    net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          while (guideKey.consumeClick()) if (client.player != null) FieldGuideScreen.open(client);
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
    net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
        dev.reterra.Content.id("farmhouse_compass"), FarmhouseCompass::extractRenderState);
    ItemTooltipCallback.EVENT.register(
        (stack, context, type, lines) -> {
          if (stack.is(Items.COARSE_DIRT) || stack.is(Items.ROOTED_DIRT))
            lines.add(Component.translatable("hint.reterra.sterile_soil").withStyle(ChatFormatting.GRAY));
          if (stack.is(Items.GLASS_BOTTLE))
            lines.add(Component.translatable("hint.reterra.empty_bottle").withStyle(ChatFormatting.GRAY));
          if (stack.getItem() instanceof net.minecraft.world.item.BlockItem b
              && dev.reterra.Content.isMachine(b.getBlock()))
            lines.add(Component.translatable("hint.reterra.machine").withStyle(ChatFormatting.GRAY));
        });
  }
}

