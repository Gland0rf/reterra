package dev.reterra.world;

import dev.reterra.Content;
import dev.reterra.Journal;
import dev.reterra.effect.AnimationMath;
import dev.reterra.effect.AnimationPayload.Kind;
import dev.reterra.effect.AnimationSignals;
import dev.reterra.machine.MachineBlock;
import dev.reterra.machine.Multiblocks;
import dev.reterra.machine.RestorationEngine;
import dev.reterra.mixin.CrystalDisplayAccess;
import dev.reterra.mixin.DisplayAnimationAccess;
import dev.reterra.state.RestorationState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Matrix4f;

/** Sixteen seconds of gathering roots, an orbiting crystal and a terrain-following release. */
public final class HeartAwakening {
  public static final int DURATION = 320;
  public static final String CRYSTAL_TAG = "reterra_awakening_crystal";

  private HeartAwakening() {}

  public static boolean start(ServerWorld world, RestorationState.Machine machine) {
    var state = RestorationState.get(world);
    if (state.heartActivated
        || state.heartAwakening > 0
        || state.completed.size() != 4
        || !world.getBlockState(machine.pos).isOf(Content.HEART)
        || Multiblocks.issue(world, machine.pos, machine.kind) != 0) return false;
    state.heartPos = machine.pos;
    state.heartAwakening = 1;
    state.heartTicks = state.heartCursor = 0;
    state.markDirty();
    world.playSound(
        null, machine.pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, .8f, .6f);
    AnimationSignals.emit(world, machine.pos, machine.pos, Kind.HEART, 0);
    return true;
  }

  public static void disable(ServerWorld world) {
    var state = RestorationState.get(world);
    AnimationSignals.stopHeart(world, state.heartPos);
    discardCrystal(world, state);
    state.heartActivated = false;
    state.heartAwakening = state.heartTicks = state.heartCursor = 0;
    state.treeJobs.values().removeIf(job -> job.heart);
    for (var machine : state.machines.values())
      if (machine.kind.equals("heart_of_the_world") && RestorationEngine.loaded(world, machine.pos))
        MachineBlock.updateVisual(world, machine);
    state.markDirty();
  }

  public static void tick(ServerWorld world, RestorationState state) {
    if (state.heartAwakening == 0
        || state.heartPos == null
        || !RestorationEngine.loaded(world, state.heartPos)) return;
    if (!world.getBlockState(state.heartPos).isOf(Content.HEART)) {
      disable(world);
      return;
    }
    if (Multiblocks.issue(world, state.heartPos, "heart_of_the_world") != 0) return;
    int age = state.heartAwakening - 1;
    if (age % 40 == 0)
      world.playSound(
          null,
          state.heartPos,
          SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
          SoundCategory.BLOCKS,
          .45f,
          .6f + age / 250f);
    AnimationSignals.emit(world, state.heartPos, state.heartPos, Kind.HEART, age);
    if (age >= 60) {
      var frame = AnimationMath.heart(age);
      crystal(
          world,
          state,
          state.heartPos.getX() + .5,
          state.heartPos.getY() + 1.5 + frame.rise(),
          state.heartPos.getZ() + .5,
          age);
    }
    state.heartAwakening += 5;
    if (state.heartAwakening > DURATION) {
      state.heartAwakening = 0;
      state.heartActivated = true;
      state.heartTicks = state.heartCursor = 0;
      discardCrystal(world, state);
      var machine = state.machines.get(state.heartPos.asLong());
      if (machine != null) MachineBlock.updateVisual(world, machine);
      AnimationSignals.emit(world, state.heartPos, state.heartPos, Kind.HEART_FINISH, 0);
      world.playSound(
          null,
          state.heartPos,
          SoundEvents.BLOCK_BEACON_POWER_SELECT,
          SoundCategory.BLOCKS,
          .8f,
          .8f);
      world.getPlayers().forEach(player -> Journal.grant(player, "new_beginning"));
    }
    state.markDirty();
  }

  private static void crystal(
      ServerWorld world, RestorationState state, double x, double y, double z, int age) {
    DisplayEntity.BlockDisplayEntity display =
        state.heartCrystalHandle != null && !state.heartCrystalHandle.isRemoved()
            ? state.heartCrystalHandle
            : state.heartCrystal == null
                ? null
                : world.getEntity(state.heartCrystal)
                        instanceof DisplayEntity.BlockDisplayEntity entity
                    ? entity
                    : null;
    if (display == null) {
      if (state.heartCrystal != null && state.heartCrystalWait < 40) {
        state.heartCrystalWait += 5;
        return;
      }
      //? if <1.21.2 {
      display = EntityType.BLOCK_DISPLAY.create(world);
      //? } else {
      /*display = EntityType.BLOCK_DISPLAY.create(world, net.minecraft.entity.SpawnReason.TRIGGERED);
      *///? }
      if (display == null) return;
      state.heartCrystal = display.getUuid();
      state.heartCrystalHandle = display;
      state.heartCrystalWait = 0;
      display.addCommandTag(CRYSTAL_TAG);
      ((CrystalDisplayAccess) display).reterra$block(Blocks.EMERALD_BLOCK.getDefaultState());
      var access = (DisplayAnimationAccess) display;
      access.reterra$movement(5);
      access.reterra$brightness(new net.minecraft.entity.decoration.Brightness(15, 15));
      access.reterra$width(4);
      access.reterra$height(8);
      display.setPosition(x, y, z);
      if (!world.spawnEntity(display)) {
        display.discard();
        state.heartCrystal = null;
        state.heartCrystalHandle = null;
        return;
      }
    }
    var frame = AnimationMath.heart(age);
    display.setPosition(x, y, z);
    var access = (DisplayAnimationAccess) display;
    access.reterra$duration(5);
    access.reterra$start(0);
    access.reterra$transform(
        new AffineTransformation(
            new Matrix4f()
                .rotateY((float) frame.spin())
                .scale((float) frame.size())
                .scale(.58f, 1.1f, .58f)
                .rotateZ((float) Math.PI / 4)
                .rotateY((float) Math.PI / 4)
                .translate(-.5f, -.5f, -.5f)));
  }

  private static void discardCrystal(ServerWorld world, RestorationState state) {
    if (state.heartCrystalHandle != null) state.heartCrystalHandle.discard();
    if (state.heartCrystal != null && world.getEntity(state.heartCrystal) != null)
      world.getEntity(state.heartCrystal).discard();
    state.heartCrystal = null;
    state.heartCrystalHandle = null;
    state.heartCrystalWait = 0;
  }
}
