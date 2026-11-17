package dev.reterra;

import static net.minecraft.commands.Commands.literal;

import dev.reterra.machine.MachineInteraction;
import dev.reterra.machine.RestorationEngine;
import dev.reterra.screen.MachineScreenHandler;
import dev.reterra.state.RestorationState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ReTerra implements ModInitializer {
  @Override
  public void onInitialize() {
    Content.initialize();
    dev.reterra.recipe.CompactSoilRecipe.initialize();
    dev.reterra.world.WorldOptions.initialize();
    dev.reterra.world.SiteLocator.initialize();
    MachineScreenHandler.initialize();
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
        .register(dev.reterra.screen.GuidePayload.ID, dev.reterra.screen.GuidePayload.CODEC);
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
        .register(
            dev.reterra.effect.AnimationPayload.ID, dev.reterra.effect.AnimationPayload.CODEC);
    ServerTickEvents.END_SERVER_TICK.register(dev.reterra.world.SiteLocator::tick);
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
        server -> dev.reterra.world.SiteLocator.clear());
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, access, environment) -> dev.reterra.world.SiteLocator.register(dispatcher));
    ServerEntityEvents.ENTITY_LOAD.register(
        (entity, world) -> {
          if (entity.entityTags().contains(dev.reterra.world.HeartAwakening.CRYSTAL_TAG)) {
            var state = RestorationState.get(world.getServer().overworld());
            if (state.heartAwakening == 0 || !entity.getUUID().equals(state.heartCrystal))
              entity.discard();
            else if (entity
                instanceof net.minecraft.world.entity.Display.BlockDisplay crystal)
              state.heartCrystalHandle = crystal;
          }
          if (dev.reterra.world.WorldOptions.barren(world)
              && entity.entityTags().contains("reterra_barren_spawn")) entity.discard();
          else if (entity instanceof net.minecraft.world.entity.animal.Animal animal)
            dev.reterra.machine.AnimalHabitatGoal.attach(animal);
        });
    ServerTickEvents.END_LEVEL_TICK.register(RestorationEngine::tick);
    ServerTickEvents.END_LEVEL_TICK.register(dev.reterra.effect.AnimationSignals::flush);
    UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
      if (player.isSpectator() || !player.getItemInHand(hand).is(net.minecraft.tags.ItemTags.HOES))
        return InteractionResult.PASS;
      var soil = world.getBlockState(hit.getBlockPos());
      if (!soil.is(net.minecraft.world.level.block.Blocks.COARSE_DIRT)
          && !soil.is(net.minecraft.world.level.block.Blocks.ROOTED_DIRT)) return InteractionResult.PASS;
      if (!world.isClientSide()) player.sendOverlayMessage(Component.translatable("hint.reterra.sterile_soil"));
      return InteractionResult.FAIL;
    });
    UseBlockCallback.EVENT.register(MachineInteraction::use);
    PlayerBlockBreakEvents.AFTER.register(
        (world, player, pos, block, entity) -> {
          if (world instanceof ServerLevel server) {
            var state = RestorationState.get(server);
            state.protectedBlocks.remove(pos.asLong());
            state.setDirty();
          }
        });
    ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
    UseItemCallback.EVENT.register(
        (player, world, hand) -> {
          ItemStack stack = player.getItemInHand(hand);
          if (stack.is(Content.RECOVERY_BUNDLE)
              || stack.is(Content.SCANNER)
              || stack.is(Content.FIELD_JOURNAL)) {
            if (player instanceof ServerPlayer server) {
              if (stack.is(Content.RECOVERY_BUNDLE)) openBundle(server, stack);
              else if (stack.is(Content.FIELD_JOURNAL)) {
                if (!dev.reterra.world.SurveyClues.follow(server, hand)) guide(server);
              } else inspect(server);
            }
            return InteractionResult.SUCCESS;
          }
          return InteractionResult.PASS;
        });
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, access, environment) ->
            dispatcher.register(
                literal("reterra")
                    .executes(
                        c -> {
                          inspect(c.getSource().getPlayerOrException());
                          return 1;
                        })
                    .then(
                        literal("status")
                            .executes(
                                c -> {
                                  inspect(c.getSource().getPlayerOrException());
                                  return 1;
                                }))
                    .then(
                        literal("heart")
                            .requires(source -> dev.reterra.compat.WorldAccess.gamemaster(source))
                            .then(
                                literal("disable")
                                    .executes(
                                        c -> {
                                          dev.reterra.world.HeartAwakening.disable(
                                              c.getSource().getServer().overworld());
                                          c.getSource()
                                              .sendSuccess(
                                                  () ->
                                                      Component.literal(
                                                          "Heart disabled. Its aura and spread"
                                                              + " boost are off."),
                                                  true);
                                          return 1;
                                        })))
                    .then(
                        literal("guide")
                            .executes(
                                c -> {
                                  guide(c.getSource().getPlayerOrException());
                                  return 1;
                                }))));
  }

  public static void onJoin(ServerPlayer player) {
    var world = dev.reterra.compat.WorldAccess.world(player).getServer().overworld();
    var state = RestorationState.get(world);
    if (state.startedPlayers.isEmpty()) {
      state.firstSitePending =
          state.protectedBlocks.isEmpty()
              && state.machines.isEmpty()
              && state.reclaimedRegions.isEmpty()
              && dev.reterra.world.WorldOptions.barren(world);
    }
    state.startedPlayers.add(player.getUUID());
    if (!state
        .discoveries
        .getOrDefault(player.getUUID(), java.util.Set.of())
        .contains("introduction")) {
      var notes =
          Component.literal(" [Field Notes]")
              .withStyle(
                  net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.UNDERLINE)
              .withStyle(
                  style ->
                      style
                          .withClickEvent(
                              dev.reterra.compat.TextEvents.run("/reterra guide"))
                          .withHoverEvent(
                              dev.reterra.compat.TextEvents.hover(Component.literal("Open Field Notes"))));
      player.sendSystemMessage(
          Component.literal(
                  dev.reterra.world.WorldOptions.barren(world)
                      ? "Find seeds and food in an old farmhouse."
                      : "Open Field Notes to start a restoration project.")
              .append(notes)
              .append(" (")
              .append(Component.keybind("key.reterra.guide"))
              .append(")"));
      Journal.discover(player, "introduction");
    }
    state.setDirty();
    Journal.grant(player, "wasteland");
  }

  public static void openBundle(net.minecraft.world.entity.player.Player player, ItemStack bundle) {
    MachineInteraction.consume(player, bundle, 1);
    MachineInteraction.give(player, new ItemStack(Items.WHEAT_SEEDS, 2));
    MachineInteraction.give(player, new ItemStack(Items.BROWN_MUSHROOM));
    if (player instanceof ServerPlayer server) Journal.grant(server, "the_last_seed");
    if (player instanceof ServerPlayer server) Journal.discover(server, "seeds");
  }

  public static void inspect(ServerPlayer player) {
    var state = RestorationState.get(dev.reterra.compat.WorldAccess.world(player));
    var core = RestorationEngine.regionAt(state, player.blockPosition());
    if (core != null
        && (player.getMainHandItem().is(Content.SCANNER)
            || player.getOffhandItem().is(Content.SCANNER))) {
      if (player.isShiftKeyDown()) {
        boolean enabled = !state.boundaryViewers.remove(player.getUUID());
        if (enabled) state.boundaryViewers.add(player.getUUID());
        player.sendOverlayMessage(
            Component.literal("Region boundary: " + (enabled ? "shown" : "hidden")));
      } else MachineScreenHandler.open(player, core, true);
    } else if (player.getMainHandItem().is(Content.SCANNER)
        || player.getOffhandItem().is(Content.SCANNER)) {
      MachineScreenHandler.preview(player, player.blockPosition());
    } else {
      var s = RestorationEngine.localSurvey(dev.reterra.compat.WorldAccess.world(player), player.blockPosition());
      player.sendSystemMessage(
          Component.literal(
              "Local survey (16 x 16): soil "
                  + s.soilPercent()
                  + "%, grass "
                  + s.grassPercent()
                  + "%, water "
                  + s.water()
                  + ", crops "
                  + s.crops()
                  + ", leaves "
                  + s.trees()
                  + ", flowers "
                  + s.flowers()
                  + ", plant types "
                  + s.species()));
    }
  }

  private static void guide(ServerPlayer player) {
    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
        player, Journal.payload(player));
  }
}
