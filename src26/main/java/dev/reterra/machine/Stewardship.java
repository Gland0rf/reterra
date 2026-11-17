package dev.reterra.machine;

import dev.reterra.ecology.Ecology;
import dev.reterra.state.RestorationState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Optional useful care actions reward active play without adding another completion gate. */
public final class Stewardship {
  private Stewardship() {}

  public static boolean plant(ServerLevel world, BlockPos pos) {
    var block = world.getBlockState(pos).getBlock();
    if (!world.getBlockState(pos).is(net.minecraft.tags.BlockTags.FLOWERS)
        && !dev.reterra.compat.GameAccess.sapling(world.getBlockState(pos))
        && !(block instanceof net.minecraft.world.level.block.VegetationBlock)
        && block != net.minecraft.world.level.block.Blocks.SUGAR_CANE
        && block != net.minecraft.world.level.block.Blocks.VINE) return false;
    var core = RestorationEngine.regionAt(RestorationState.get(world), pos);
    if (core == null) return false;
    if (dev.reterra.ecology.HabitatPlants.nativePlant(
        core.family, core.biome, world.getBlockState(pos))) return credit(world, pos, "plant");
    if (!core.reclaimed && core.stableSeconds > 0 && !core.careTasks.contains("plant"))
      for (var player : world.players())
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 12 * 12)
          player.sendOverlayMessage(
              net.minecraft.network.chat.Component.literal(
                  "For local habitat care, "
                      + dev.reterra.ecology.HabitatPlants.careHint(
                          core.family, dev.reterra.ecology.HabitatPlants.variant(core.biome))
                      + "."));
    return false;
  }

  public static boolean credit(ServerLevel world, BlockPos pos, String task) {
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
    world.sendParticles(
        net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
        pos.getX() + .5,
        pos.getY() + 1,
        pos.getZ() + .5,
        6,
        .4,
        .4,
        .4,
        .02);
    for (var player : world.players())
      if (RestorationEngine.bounds(core).contains(dev.reterra.compat.WorldAccess.position(player)))
        player.sendOverlayMessage(
            net.minecraft.network.chat.Component.literal("Habitat care: +15 healthy seconds"));
    state.setDirty();
    return true;
  }
}
