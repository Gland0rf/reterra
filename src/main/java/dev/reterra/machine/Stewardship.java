package dev.reterra.machine;

import dev.reterra.ecology.Ecology;
import dev.reterra.state.RestorationState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Optional useful care actions reward active play without adding another completion gate. */
public final class Stewardship {
  private Stewardship() {}

  public static boolean plant(ServerWorld world, BlockPos pos) {
    var block = world.getBlockState(pos).getBlock();
    if (!world.getBlockState(pos).isIn(net.minecraft.registry.tag.BlockTags.FLOWERS)
        && !world.getBlockState(pos).isIn(net.minecraft.registry.tag.BlockTags.SAPLINGS)
        && !(block instanceof net.minecraft.block.PlantBlock)
        && block != net.minecraft.block.Blocks.SUGAR_CANE
        && block != net.minecraft.block.Blocks.VINE) return false;
    var core = RestorationEngine.regionAt(RestorationState.get(world), pos);
    if (core == null) return false;
    if (dev.reterra.ecology.HabitatPlants.nativePlant(
        core.family, core.biome, world.getBlockState(pos))) return credit(world, pos, "plant");
    if (!core.reclaimed && core.stableSeconds > 0 && !core.careTasks.contains("plant"))
      for (var player : world.getPlayers())
        if (player.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(pos)) < 12 * 12)
          player.sendMessage(
              net.minecraft.text.Text.literal(
                  "For local habitat care, "
                      + dev.reterra.ecology.HabitatPlants.careHint(
                          core.family, dev.reterra.ecology.HabitatPlants.variant(core.biome))
                      + "."),
              true);
    return false;
  }

  public static boolean credit(ServerWorld world, BlockPos pos, String task) {
    if (!java.util.Set.of("plant", "feed", "tend").contains(task)) return false;
    var state = RestorationState.get(world);
    var core = RestorationEngine.regionAt(state, pos);
    if (core == null
        || core.reclaimed
        || core.paused
        || core.careTasks.size() >= 3
        || core.careTasks.contains(task)
        || core.stableSeconds >= Ecology.stabilityTarget(core.introductory)
        || Multiblocks.issue(world, core.pos, core.kind) != 0
        || !RestorationEngine.allSectorsLoaded(world, core)
        || !java.util.Arrays.stream(core.sectors).allMatch(s -> s.surveyed)
        || core.survey(RestorationEngine.animalSpecies(world, core))
                .stage(core.family, core.introductory)
            != Ecology.Stage.RECLAIMED) return false;
    core.careTasks.add(task);
    core.stableSeconds =
        Math.min(Ecology.stabilityTarget(core.introductory), core.stableSeconds + 15);
    world.spawnParticles(
        net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
        pos.getX() + .5,
        pos.getY() + 1,
        pos.getZ() + .5,
        6,
        .4,
        .4,
        .4,
        .02);
    for (var player : world.getPlayers())
      if (RestorationEngine.bounds(core).contains(dev.reterra.compat.WorldAccess.position(player)))
        player.sendMessage(
            net.minecraft.text.Text.literal("Habitat care: +15 healthy seconds"), true);
    state.markDirty();
    return true;
  }
}
