package dev.reterra;

import dev.reterra.effect.AnimationPayload.Kind;
import dev.reterra.effect.AnimationSignals;
import dev.reterra.machine.Multiblocks;
import dev.reterra.state.RestorationState;
import dev.reterra.world.HeartAwakening;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
//? if <1.21.11 {
import net.minecraft.world.GameRules;
//? }
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

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

  private static void teleport(net.minecraft.server.network.ServerPlayerEntity player,
      ServerWorld world, double x, double y, double z, float yaw, float pitch) {
    //? if <1.21.2 {
    player.teleport(world, x, y, z, yaw, pitch);
    //? } else {
    /*player.teleport(world, x, y, z, java.util.Set.of(), yaw, pitch, false);
    *///? }
  }

  @Override
  public void onInitializeClient() {
    if (!Boolean.getBoolean("reterra.animation-preview")) return;
    ServerTickEvents.END_WORLD_TICK.register(
        world -> {
          if (!world.getRegistryKey().equals(World.OVERWORLD) || !ready) return;
          elapsed++;
          if (elapsed == -40)
            world
                .getPlayers()
                .forEach(
                    player -> {
                      player.changeGameMode(GameMode.CREATIVE);
                      teleport(player, world, .5, 100, -4.5, 180, 0);
                      dev.reterra.screen.MachineScreenHandler.open(
                          player, RestorationState.get(world).machines.get(HEART.asLong()), false);
                      player.currentScreenHandler.setCursorStack(
                          new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_INGOT, 3));
                      player.currentScreenHandler.sendContentUpdates();
                    });
          if (elapsed == 0) {
            if (!navigationChecked) {
              elapsed = -1;
              return;
            }
            world
                .getPlayers()
                .forEach(
                    player -> {
                      player.changeGameMode(GameMode.SPECTATOR);
                      teleport(player, world, 17, 106, 16, 135, 14);
                    });
            HeartAwakening.start(world, RestorationState.get(world).machines.get(HEART.asLong()));
          }
          if (elapsed == 40)
            world.getPlayers().forEach(player -> teleport(player, world, 8, 104, 7, 152, 1));
          if (elapsed % 5 == 0) {
            for (int i = 0; i < 4; i++)
              AnimationSignals.emit(
                  world,
                  MACHINES[i],
                  i == 3 ? MACHINES[i] : MACHINES[i].south(4).down(),
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
                .getSoundVolumeOption(net.minecraft.sound.SoundCategory.MASTER)
                .setValue(0.0);
            client.getSoundManager().stopAll();
            muted = true;
          }
          GLFW.glfwHideWindow(client.getWindow().getHandle());
          client.options.pauseOnLostFocus = false;
          if (client.getOverlay() == null && client.currentScreen != null && client.world == null) {
            String name = client.currentScreen.getClass().getSimpleName();
            if (!name.equals(lastScreen)) {
              System.out.println(
                  "Animation preview screen: "
                      + name
                      + " / "
                      + client.currentScreen.getTitle().getString());
              lastScreen = name;
            }
            if (client.currentScreen
                instanceof net.minecraft.client.gui.screen.AccessibilityOnboardingScreen)
              client.currentScreen.close();
            else if (client.currentScreen instanceof net.minecraft.client.gui.screen.ConfirmScreen)
              for (var child : client.currentScreen.children())
                if (child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                    && (button.getMessage().getString().equals("Proceed")
                        || button.getMessage().getString().equals("Continue"))) {
                  PreviewInput.press(button);
                  break;
                }
          }
          if (++waiting > 2400) throw new IllegalStateException("Animation preview timed out.");
          if (client.world == null || client.player == null || client.getServer() == null) return;
          if (client.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen)
            client.setScreen(null);
          if (!requested) {
            requested = true;
            client.options.hudHidden = true;
            client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
            client.options.getViewDistance().setValue(6);
            client.setScreen(null);
            client.getServer().execute(() -> setup(client.getServer().getOverworld()));
          }F
          if (!navigationChecked
              && client.currentScreen instanceof dev.reterra.client.MachineScreen machine
              && client.player.currentScreenHandler.getCursorStack().getCount() == 3) {
            var handler = client.player.currentScreenHandler;
            PreviewInput.press(machine.children().stream()
                .filter(
                    child ->
                        child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                            && button.getMessage().getString().equals("Recipes"))
                .map(child -> (net.minecraft.client.gui.widget.ButtonWidget) child)
                .findFirst()
                .orElseThrow());
            if (!(client.currentScreen instanceof dev.reterra.client.RecipeBrowserScreen browser))
              throw new AssertionError("A machine's Recipe button must open the recipe browser.");
            if (client.player.currentScreenHandler != handler
                || handler.getCursorStack().getCount() != 3)
              throw new AssertionError("Opening recipes must preserve the machine and held stack.");
            browser.close();
            if (!(client.currentScreen instanceof dev.reterra.client.MachineScreen)
                || client.player.currentScreenHandler != handler
                || handler.getCursorStack().getCount() != 3)
              throw new AssertionError(
                  "Back must return to the same machine without dropping the held stack.");
            PreviewInput.press(((dev.reterra.client.MachineScreen) client.currentScreen)
                .children().stream()
                    .filter(
                        child ->
                            child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                                && button.getMessage().getString().equals("Recipes"))
                    .map(child -> (net.minecraft.client.gui.widget.ButtonWidget) child)
                    .findFirst()
                    .orElseThrow());
            dev.reterra.client.FieldGuideScreen.open(client);
            if (!(client.currentScreen instanceof dev.reterra.client.FieldGuideScreen)
                || client.player.currentScreenHandler != client.player.playerScreenHandler)
              throw new AssertionError(
                  "Opening Field Notes from recipes must release the hidden machine menu.");
            client.setScreen(null);
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
                      client.scheduleStop();
                    }
                  });
            } catch (java.io.IOException error) {
              throw new IllegalStateException("Cannot save animation preview", error);
            }
          }
        });
  }

  private void setup(ServerWorld world) {
    dev.reterra.world.WorldOptions.setBarren(world, false);
    //? if <1.21.11 {
    world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, world.getServer());
    world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, world.getServer());
    //? } else {
    /*world.getGameRules().setValue(net.minecraft.world.rule.GameRules.ADVANCE_TIME, false, world.getServer());
    world.getGameRules().setValue(net.minecraft.world.rule.GameRules.DO_MOB_SPAWNING, false, world.getServer());
    *///? }
    world.setTimeOfDay(10500);
    world.setWeather(6000, 0, false, false);
    for (int cx = -3; cx <= 3; cx++)
      for (int cz = -3; cz <= 3; cz++) {
        world.setChunkForced(cx, cz, true);
        world.getChunk(cx, cz);
      }
    for (int x = -44; x <= 44; x++)
      for (int z = -44; z <= 44; z++) {
        world.setBlockState(new BlockPos(x, 98, z), Blocks.STONE.getDefaultState());
        var soil = x < -18 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK;
        world.setBlockState(new BlockPos(x, 99, z), soil.getDefaultState());
      }
    var state = RestorationState.get(world);
    state.machines.clear();
    state.reclaimedRegions.clear();
    HeartAwakening.disable(world);
    for (int i = 0; i < 4; i++) {
      world.setBlockState(
          MACHINES[i],
          new net.minecraft.block.Block[] {
                Content.RECLAIMER, Content.CORE, Content.WATERWORKS, Content.CHAMBER
              }
              [i].getDefaultState());
      for (var part : Multiblocks.parts(KINDS[i])) {
        var block = part.block().getDefaultState();
        if (block.isOf(Blocks.WATER_CAULDRON))
          block = block.with(net.minecraft.block.LeveledCauldronBlock.LEVEL, 3);
        world.setBlockState(MACHINES[i].add(part.x(), part.y(), part.z()), block);
      }
    }
    world.setBlockState(HEART, Content.HEART.getDefaultState());
    for (var part : Multiblocks.parts("heart_of_the_world"))
      world.setBlockState(HEART.add(part.x(), part.y(), part.z()), part.block().getDefaultState());
    state.completed.addAll(java.util.EnumSet.allOf(dev.reterra.ecology.Ecology.Family.class));
    var heart = new RestorationState.Machine(HEART, "heart_of_the_world");
    state.machines.put(HEART.asLong(), heart);
    world
        .getPlayers()
        .forEach(
            player -> {
              player.changeGameMode(GameMode.SPECTATOR);
              teleport(player, world, 17, 106, 16, 135, 14);
            });
    elapsed = -60;
    ready = true;
  }
}
