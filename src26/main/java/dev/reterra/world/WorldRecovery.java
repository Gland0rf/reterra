package dev.reterra.world;

import dev.reterra.machine.RestorationEngine;
import dev.reterra.state.RestorationState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/** Shared finale and optional survey boundaries; both operate only in loaded terrain. */
public final class WorldRecovery {
  private WorldRecovery() {}

  public static void tick(ServerLevel world, RestorationState state) {
    state.previewBorders.entrySet().removeIf(entry -> entry.getValue().until() < world.getGameTime());
    for (var player : world.players())
      if (state.boundaryViewers.contains(player.getUUID())
          || state.previewBorders.containsKey(player.getUUID())) {
        var core = RestorationEngine.regionAt(state, player.blockPosition());
        var preview = state.previewBorders.get(player.getUUID());
        BlockPos center = preview != null ? preview.pos() : core == null ? null : core.pos;
        if (center == null
            || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(center)) > 128 * 128)
          continue;
        int x = ((center.getX() >> 4) - 1) * 16, z = ((center.getZ() >> 4) - 1) * 16;
        for (int offset = 0; offset <= 48; offset += 4) {
          marker(world, player, x + offset, z);
          marker(world, player, x + offset, z + 47);
          marker(world, player, x, z + offset);
          marker(world, player, x + 47, z + offset);
        }
      }
    if (state.heartActivated && state.heartPos == null)
      state.machines.values().stream()
          .filter(m -> m.kind.equals("heart_of_the_world"))
          .findFirst()
          .ifPresent(m -> state.heartPos = m.pos);
    if (!state.heartActivated
        || state.heartPos == null
        || !RestorationEngine.loaded(world, state.heartPos)) return;
    state.heartTicks = Math.min(1200, state.heartTicks + 20);
    var heart = new RestorationState.Machine(state.heartPos, "heart_of_the_world");
    heart.spreadRadius = 8;
    heart.rewildCursor = state.heartCursor;
    for (int i = 0; i < 4; i++) RestorationEngine.rewild(world, state, heart);
    state.heartCursor = heart.rewildCursor;
    double radius = 1.7;
    for (int i = 0; i < 12; i++) {
      double angle = i * Math.PI / 12 + world.getGameTime() * .02;
      world.sendParticles(
          ParticleTypes.END_ROD,
          state.heartPos.getX() + .5 + Math.cos(angle) * radius,
          state.heartPos.getY() + 1 + Math.sin(angle * 2) * .4,
          state.heartPos.getZ() + .5 + Math.sin(angle) * radius,
          1,
          0,
          .05,
          0,
          .01);
    }
    state.setDirty();
  }

  public static void ambience(ServerLevel world, RestorationState.Machine core) {
    if ((!core.registered && !core.reclaimed)
        || Math.floorMod(world.getGameTime() / 20 + core.pos.asLong(), 20) != 0
        || (!core.reclaimed && core.survey(0).stage(core.family, core.introductory).ordinal() < 2))
      return;
    BlockPos ground =
        RestorationEngine.ground(
            world,
            core.pos.getX() + world.getRandom().nextInt(17) - 8,
            core.pos.getZ() + world.getRandom().nextInt(17) - 8);
    if (ground == null) return;
    boolean water = world.getBlockState(ground).is(net.minecraft.world.level.block.Blocks.WATER);
    world.sendParticles(
        water ? ParticleTypes.BUBBLE_POP : ParticleTypes.SPORE_BLOSSOM_AIR,
        ground.getX() + .5,
        ground.getY() + 1,
        ground.getZ() + .5,
        3,
        2,
        .4,
        2,
        .01);
    if (water || core.survey(0).trees() > 0 || core.restoredSpecies.contains("minecraft:bee"))
      world.playSound(
          null,
          ground,
          water
              ? net.minecraft.sounds.SoundEvents.WATER_AMBIENT
              : core.restoredSpecies.contains("minecraft:bee")
                  ? net.minecraft.sounds.SoundEvents.BEE_LOOP
                  : net.minecraft.sounds.SoundEvents.AZALEA_LEAVES_FALL,
          net.minecraft.sounds.SoundSource.AMBIENT,
          .16f,
          .9f + world.getRandom().nextFloat() * .2f);
  }

  private static void marker(
      ServerLevel world, net.minecraft.server.level.ServerPlayer player, int x, int z) {
    BlockPos ground = RestorationEngine.ground(world, x, z);
    if (ground != null)
      world.sendParticles(
          player,
          ParticleTypes.COMPOSTER,
          true,
          false,
          x + .5,
          ground.getY() + 1.15,
          z + .5,
          1,
          0,
          .2,
          0,
          0);
  }
}
