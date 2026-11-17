package dev.reterra;

import static net.minecraft.server.command.CommandManager.literal;

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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
//? if <1.21.2 {
import net.minecraft.util.TypedActionResult;
//? } else {
/*import net.minecraft.util.ActionResult;
*///? }

public final class ReTerra implements ModInitializer {
  @Override
  public void onInitialize() {
    Content.initialize();
    dev.reterra.recipe.CompactSoilRecipe.initialize();
    dev.reterra.world.WorldOptions.initialize();
    dev.reterra.world.SiteLocator.initialize();
    MachineScreenHandler.initialize();
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
        .register(dev.reterra.screen.GuidePayload.ID, dev.reterra.screen.GuidePayload.CODEC);
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
        .register(
            dev.reterra.effect.AnimationPayload.ID, dev.reterra.effect.AnimationPayload.CODEC);
    ServerTickEvents.END_SERVER_TICK.register(dev.reterra.world.SiteLocator::tick);
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
        server -> dev.reterra.world.SiteLocator.clear());
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, access, environment) -> dev.reterra.world.SiteLocator.register(dispatcher));
    ServerEntityEvents.ENTITY_LOAD.register(
        (entity, world) -> {
          if (entity.getCommandTags().contains(dev.reterra.world.HeartAwakening.CRYSTAL_TAG)) {
            var state = RestorationState.get(world.getServer().getOverworld());
            if (state.heartAwakening == 0 || !entity.getUuid().equals(state.heartCrystal))
              entity.discard();
            else if (entity
                instanceof net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity crystal)
              state.heartCrystalHandle = crystal;
          }
          if (dev.reterra.world.WorldOptions.barren(world)
              && entity.getCommandTags().contains("reterra_barren_spawn")) entity.discard();
          else if (entity instanceof net.minecraft.entity.passive.AnimalEntity animal)
            dev.reterra.machine.AnimalHabitatGoal.attach(animal);
        });
    ServerTickEvents.END_WORLD_TICK.register(RestorationEngine::tick);
    ServerTickEvents.END_WORLD_TICK.register(dev.reterra.effect.AnimationSignals::flush);
    UseBlockCallback.EVENT.register(MachineInteraction::use);
    PlayerBlockBreakEvents.AFTER.register(
        (world, player, pos, block, entity) -> {
          if (world instanceof ServerWorld server) {
            var state = RestorationState.get(server);
            state.protectedBlocks.remove(pos.asLong());
            state.markDirty();
          }
        });
    ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
    UseItemCallback.EVENT.register(
        (player, world, hand) -> {
          ItemStack stack = player.getStackInHand(hand);
          if (stack.isOf(Content.RECOVERY_BUNDLE)
              || stack.isOf(Content.SCANNER)
              || stack.isOf(Content.FIELD_JOURNAL)) {
            if (player instanceof ServerPlayerEntity server) {
              if (stack.isOf(Content.RECOVERY_BUNDLE)) openBundle(server, stack);
              else if (stack.isOf(Content.FIELD_JOURNAL)) {
                if (!dev.reterra.world.SurveyClues.follow(server, hand)) guide(server);
              } else inspect(server);
            }
            //? if <1.21.2 {
            return TypedActionResult.success(stack, world.isClient());
            //? } else {
            /*return ActionResult.SUCCESS;
            *///? }
          }
          //? if <1.21.2 {
          return TypedActionResult.pass(stack);
          //? } else {
          /*return ActionResult.PASS;
          *///? }
        });
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, access, environment) ->
            dispatcher.register(
                literal("reterra")
                    .executes(
                        c -> {
                          inspect(c.getSource().getPlayerOrThrow());
                          return 1;
                        })
                    .then(
                        literal("status")
                            .executes(
                                c -> {
                                  inspect(c.getSource().getPlayerOrThrow());
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
                                              c.getSource().getServer().getOverworld());
                                          c.getSource()
                                              .sendFeedback(
                                                  () ->
                                                      Text.literal(
                                                          "Heart disabled. Its aura and spread"
                                                              + " boost are off."),
                                                  true);
                                          return 1;
                                        })))
                    .then(
                        literal("guide")
                            .executes(
                                c -> {
                                  guide(c.getSource().getPlayerOrThrow());
                                  return 1;
                                }))));
  }

  public static void onJoin(ServerPlayerEntity player) {
    var world = dev.reterra.compat.WorldAccess.world(player).getServer().getOverworld();
    var state = RestorationState.get(world);
    if (state.startedPlayers.isEmpty()) {
      state.firstSitePending =
          state.protectedBlocks.isEmpty()
              && state.machines.isEmpty()
              && state.reclaimedRegions.isEmpty()
              && dev.reterra.world.WorldOptions.barren(world);
    }
    state.startedPlayers.add(player.getUuid());
    if (!state
        .discoveries
        .getOrDefault(player.getUuid(), java.util.Set.of())
        .contains("introduction")) {
      var notes =
          Text.literal(" [Field Notes]")
              .formatted(
                  net.minecraft.util.Formatting.GREEN, net.minecraft.util.Formatting.UNDERLINE)
              .styled(
                  style ->
                      style
                          .withClickEvent(
                              dev.reterra.compat.TextEvents.run("/reterra guide"))
                          .withHoverEvent(
                              dev.reterra.compat.TextEvents.hover(Text.literal("Open Field Notes"))));
      player.sendMessage(
          Text.literal(
                  dev.reterra.world.WorldOptions.barren(world)
                      ? "Find seeds and food in an old farmhouse."
                      : "Open Field Notes to start a restoration project.")
              .append(notes)
              .append(" (")
              .append(Text.keybind("key.reterra.guide"))
              .append(")"),
          false);
      Journal.discover(player, "introduction");
    }
    state.markDirty();
    Journal.grant(player, "wasteland");
  }

  public static void openBundle(net.minecraft.entity.player.PlayerEntity player, ItemStack bundle) {
    MachineInteraction.consume(player, bundle, 1);
    MachineInteraction.give(player, new ItemStack(Items.WHEAT_SEEDS, 2));
    MachineInteraction.give(player, new ItemStack(Items.BROWN_MUSHROOM));
    if (player instanceof ServerPlayerEntity server) Journal.grant(server, "the_last_seed");
    if (player instanceof ServerPlayerEntity server) Journal.discover(server, "seeds");
  }

  public static void inspect(ServerPlayerEntity player) {
    var state = RestorationState.get(dev.reterra.compat.WorldAccess.world(player));
    var core = RestorationEngine.regionAt(state, player.getBlockPos());
    if (core != null
        && (player.getMainHandStack().isOf(Content.SCANNER)
            || player.getOffHandStack().isOf(Content.SCANNER))) {
      if (player.isSneaking()) {
        boolean enabled = !state.boundaryViewers.remove(player.getUuid());
        if (enabled) state.boundaryViewers.add(player.getUuid());
        player.sendMessage(
            Text.literal("Region boundary: " + (enabled ? "shown" : "hidden")), true);
      } else MachineScreenHandler.open(player, core, true);
    } else if (player.getMainHandStack().isOf(Content.SCANNER)
        || player.getOffHandStack().isOf(Content.SCANNER)) {
      MachineScreenHandler.preview(player, player.getBlockPos());
    } else {
      var s = RestorationEngine.localSurvey(dev.reterra.compat.WorldAccess.world(player), player.getBlockPos());
      player.sendMessage(
          Text.literal(
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
                  + s.species()),
          false);
    }
  }

  private static void guide(ServerPlayerEntity player) {
    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
        player, Journal.payload(player));
  }
}
