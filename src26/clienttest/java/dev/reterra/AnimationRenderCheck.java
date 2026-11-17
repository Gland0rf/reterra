package dev.reterra;

import dev.reterra.effect.AnimationPayload.Kind;
import dev.reterra.effect.AnimationSignals;
import dev.reterra.machine.Multiblocks;
import dev.reterra.state.RestorationState;
import dev.reterra.world.HeartAwakening;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Launch only in the disposable AnimationCheck world to inspect real world rendering and packets.
 */
public final class AnimationRenderCheck implements ClientModInitializer {
  private static final BlockPos HEART = new BlockPos(0, 100, -8);
  private static final BlockPos[] MACHINES = {
    new BlockPos(-12, 100, 0), new BlockPos(-6, 100, 0),
    new BlockPos(6, 100, 0), new BlockPos(12, 100, 0)
  };
  private static final String[] KINDS = {
    "soil_reclaimer", "restoration_core", "water_restorer", "revival_chamber"
  };
  private static final int[] SHOTS = {25, 105, 195, 260, 315, 345};
  private boolean requested, ready, muted, capturing;
  private volatile boolean navigationChecked;
  private volatile int elapsed = -1;
  private int shot, waiting;
  private String lastScreen = "";

  private static void teleport(net.minecraft.server.level.ServerPlayer player,
      ServerLevel world, double x, double y, double z, float yaw, float pitch) {
    player.teleportTo(world, x, y, z, java.util.Set.of(), yaw, pitch, false);
  }

  @Override
  public void onInitializeClient() {
    if (!Boolean.getBoolean("reterra.animation-preview")) return;
    ServerTickEvents.END_LEVEL_TICK.register(
        world -> {
          if (!world.dimension().equals(Level.OVERWORLD) || !ready) return;
          elapsed++;
          if (elapsed == -40)
            world
                .players()
                .forEach(
                    player -> {
                      player.setGameMode(GameType.CREATIVE);
                      teleport(player, world, .5, 100, -4.5, 180, 0);
                      dev.reterra.screen.MachineScreenHandler.open(
                          player, RestorationState.get(world).machines.get(HEART.asLong()), false);
                      player.containerMenu.setCarried(
                          new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 3));
                      player.containerMenu.broadcastChanges();
                    });
          if (elapsed == 0) {
            if (!navigationChecked) {
              elapsed = -1;
              return;
            }
            world
                .players()
                .forEach(
                    player -> {
                      player.setGameMode(GameType.SPECTATOR);
                      teleport(player, world, 17, 106, 16, 135, 14);
                    });
            HeartAwakening.start(world, RestorationState.get(world).machines.get(HEART.asLong()));
          }
          if (elapsed == 40)
            world.players().forEach(player -> teleport(player, world, 8, 104, 7, 152, 1));
          if (elapsed % 5 == 0) {
            for (int i = 0; i < 4; i++)
              AnimationSignals.emit(
                  world,
                  MACHINES[i],
                  i == 3 ? MACHINES[i] : MACHINES[i].south(4).below(),
                  Kind.values()[i],
                  Math.clamp(elapsed / 400f, 0, 1));
            AnimationSignals.flush(world);
          }
        });
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          if (!muted) {
            client
                .options
                .getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER)
                .set(0.0);
            client.getSoundManager().stop();
            muted = true;
          }
          PreviewWorld.hide(client);
          client.options.pauseOnLostFocus = false;
          if (!PreviewWorld.loading(client) && dev.reterra.client.ClientAccess.screen(client) != null && client.level == null) {
            String name = dev.reterra.client.ClientAccess.screen(client).getClass().getSimpleName();
            if (!name.equals(lastScreen)) {
              System.out.println(
                  "Animation preview screen: "
                      + name
                      + " / "
                      + dev.reterra.client.ClientAccess.screen(client).getTitle().getString());
              lastScreen = name;
            }
            PreviewWorld.dismissLoadPrompt(client);
          }
          if (++waiting > 2400) throw new IllegalStateException("Animation preview timed out.");
          if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
          if (dev.reterra.client.ClientAccess.screen(client) instanceof net.minecraft.client.gui.screens.PauseScreen)
            dev.reterra.client.ClientAccess.screen(client, null);
          if (!requested) {
            requested = true;
            PreviewWorld.hideHud(client);
            client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            client.options.renderDistance().set(6);
            dev.reterra.client.ClientAccess.screen(client, null);
            client.getSingleplayerServer().execute(() -> setup(client.getSingleplayerServer().overworld()));
          }
          if (!navigationChecked
              && dev.reterra.client.ClientAccess.screen(client) instanceof dev.reterra.client.MachineScreen machine
              && client.player.containerMenu.getCarried().getCount() == 3) {
            var handler = client.player.containerMenu;
            PreviewInput.press(machine.children().stream()
                .filter(
                    child ->
                        child instanceof net.minecraft.client.gui.components.Button button
                            && button.getMessage().getString().equals("Recipes"))
                .map(child -> (net.minecraft.client.gui.components.Button) child)
                .findFirst()
                .orElseThrow());
            if (!(dev.reterra.client.ClientAccess.screen(client) instanceof dev.reterra.client.RecipeBrowserScreen browser))
              throw new AssertionError("A machine's Recipe button must open the recipe browser.");
            if (client.player.containerMenu != handler
                || handler.getCarried().getCount() != 3)
              throw new AssertionError("Opening recipes must preserve the machine and held stack.");
            browser.onClose();
            if (!(dev.reterra.client.ClientAccess.screen(client) instanceof dev.reterra.client.MachineScreen)
                || client.player.containerMenu != handler
                || handler.getCarried().getCount() != 3)
              throw new AssertionError(
                  "Back must return to the same machine without dropping the held stack.");
            PreviewInput.press(((dev.reterra.client.MachineScreen) dev.reterra.client.ClientAccess.screen(client))
                .children().stream()
                    .filter(
                        child ->
                            child instanceof net.minecraft.client.gui.components.Button button
                                && button.getMessage().getString().equals("Recipes"))
                    .map(child -> (net.minecraft.client.gui.components.Button) child)
                    .findFirst()
                    .orElseThrow());
            dev.reterra.client.FieldGuideScreen.open(client);
            if (!(dev.reterra.client.ClientAccess.screen(client) instanceof dev.reterra.client.FieldGuideScreen)
                || client.player.containerMenu != client.player.inventoryMenu)
              throw new AssertionError(
                  "Opening Field Notes from recipes must release the hidden machine menu.");
            dev.reterra.client.ClientAccess.screen(client, null);
            navigationChecked = true;
            System.out.println(
                "Machine recipe navigation, cursor preservation and guide shortcut passed.");
          }
          if (!capturing && shot < SHOTS.length && elapsed >= SHOTS[shot]) {
            try {
              var folder = java.nio.file.Path.of("animation-screenshots");
              java.nio.file.Files.createDirectories(folder);
              capturing = true;
              PreviewCapture.save(client,
                  folder.resolve(shot == 0 ? "machines.png" : "heart-" + SHOTS[shot] + ".png"), () -> {
                    capturing = false;
                    if (++shot == SHOTS.length) {
                      if (!navigationChecked)
                        throw new AssertionError("The machine recipe round trip was not exercised.");
                      client.stop();
                    }
                  });
            } catch (java.io.IOException error) {
              throw new IllegalStateException("Cannot save animation preview", error);
            }
          }
        });
  }

  private void setup(ServerLevel world) {
    dev.reterra.world.WorldOptions.setBarren(world, false);
    world.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, false, world.getServer());
    world.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, false, world.getServer());
    PreviewWorld.time(world, 10500);
    world.getWeatherData().setRaining(false);
    world.getWeatherData().setThundering(false);
    world.getWeatherData().setClearWeatherTime(6000);
    for (int cx = -3; cx <= 3; cx++)
      for (int cz = -3; cz <= 3; cz++) {
        world.setChunkForced(cx, cz, true);
        world.getChunk(cx, cz);
      }
    for (int x = -44; x <= 44; x++)
      for (int z = -44; z <= 44; z++) {
        world.setBlockAndUpdate(new BlockPos(x, 98, z), Blocks.STONE.defaultBlockState());
        var soil = x < -18 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK;
        world.setBlockAndUpdate(new BlockPos(x, 99, z), soil.defaultBlockState());
      }
    var state = RestorationState.get(world);
    state.machines.clear();
    state.reclaimedRegions.clear();
    HeartAwakening.disable(world);
    for (int i = 0; i < 4; i++) {
      world.setBlockAndUpdate(
          MACHINES[i],
          new net.minecraft.world.level.block.Block[] {
                Content.RECLAIMER, Content.CORE, Content.WATERWORKS, Content.CHAMBER
              }
              [i].defaultBlockState());
      for (var part : Multiblocks.parts(KINDS[i])) {
        var block = part.block().defaultBlockState();
        if (block.is(Blocks.WATER_CAULDRON))
          block = block.setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3);
        world.setBlockAndUpdate(MACHINES[i].offset(part.x(), part.y(), part.z()), block);
      }
    }
    world.setBlockAndUpdate(HEART, Content.HEART.defaultBlockState());
    for (var part : Multiblocks.parts("heart_of_the_world"))
      world.setBlockAndUpdate(HEART.offset(part.x(), part.y(), part.z()), part.block().defaultBlockState());
    state.completed.addAll(java.util.EnumSet.allOf(dev.reterra.ecology.Ecology.Family.class));
    var heart = new RestorationState.Machine(HEART, "heart_of_the_world");
    state.machines.put(HEART.asLong(), heart);
    world
        .players()
        .forEach(
            player -> {
              player.setGameMode(GameType.SPECTATOR);
              teleport(player, world, 17, 106, 16, 135, 14);
            });
    elapsed = -60;
    ready = true;
  }
}
