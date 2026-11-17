package dev.reterra.world;

import dev.reterra.machine.RestorationEngine;
import dev.reterra.state.RestorationState;
import dev.reterra.state.RestorationState.Machine;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/**
 * Bounded surface recovery: never excavates terrain, fills caves, or works outside river biomes.
 */
public final class RiverRecovery {
  private RiverRecovery() {}

  public static int tick(ServerWorld world, Machine pump) {
    if (pump.water < 1 || pump.fuel < 1 || pump.compost < 1) return 0;
    int changed = 0;
    for (int i = 0; i < 64 && changed < 2 && pump.water > 0 && pump.fuel > 0; i++) {
      int index = Math.floorMod(pump.riverCursor++, 25 * 25);
      if (restoreColumn(
          world, pump.pos.getX() + index % 25 - 12, pump.pos.getZ() + index / 25 - 12)) {
        pump.water--;
        pump.fuel--;
        changed++;
        pump.riverRestored++;
        dev.reterra.effect.AnimationSignals.emit(
            world,
            pump.pos,
            new BlockPos(
                pump.pos.getX() + index % 25 - 12,
                world.getSeaLevel() - 1,
                pump.pos.getZ() + index / 25 - 12),
            dev.reterra.effect.AnimationPayload.Kind.WATER,
            0);
      }
    }
    if (changed > 0) {
      pump.compost--;
      world.spawnParticles(
          net.minecraft.particle.ParticleTypes.SPLASH,
          pump.pos.getX() + .5,
          pump.pos.getY() + 1,
          pump.pos.getZ() + .5,
          8,
          .4,
          .3,
          .4,
          .02);
    }
    pump.markDirty();
    return changed;
  }

  public static boolean restoreColumn(ServerWorld world, int x, int z) {
    int surface = world.getSeaLevel() - 1;
    BlockPos top = new BlockPos(x, surface, z);
    if (!RestorationEngine.loaded(world, top)) return false;
    String biome = world.getBiome(top).getKey().map(k -> k.getValue().getPath()).orElse("");
    if (!biome.equals("river") && !biome.equals("frozen_river")) return false;
    // Anything above the historic waterline is a bank, roof or build, never a channel to flood.
    if (world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) > surface + 1) return false;
    var state = RestorationState.get(world);
    BlockPos bed = null;
    for (int y = surface; y >= Math.max(54, surface - 8); y--) {
      BlockPos p = new BlockPos(x, y, z);
      if (state.protectedBlocks.contains(p.asLong()) || world.getBlockEntity(p) != null)
        return false;
      var b = world.getBlockState(p);
      if (!b.isAir() && !b.isOf(Blocks.WATER)) {
        if (!(b.isIn(BlockTags.DIRT)
            || b.isOf(Blocks.GRAVEL)
            || b.isOf(Blocks.SAND)
            || b.isOf(Blocks.CLAY)
            || b.isOf(Blocks.STONE)
            || b.isOf(Blocks.MUD))) return false;
        bed = p;
        break;
      }
    }
    if (bed == null || bed.getY() >= surface) return false;
    // A channel must have a solid bottom, not an open shaft into a cave.
    if (!dev.reterra.compat.WorldAccess.opaque(world, bed.down())) return false;
    for (int y = bed.getY() + 1; y <= surface; y++) {
      BlockPos p = new BlockPos(x, y, z);
      if (world.getBlockState(p).isOf(Blocks.WATER) && world.getFluidState(p).isStill()) continue;
      world.setBlockState(p, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
      return true;
    }
    return false;
  }
}
