package dev.reterra.machine;

import dev.reterra.mixin.MobGoalsAccessor;
import dev.reterra.state.RestorationState;
import java.util.EnumSet;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Revived wildlife wanders normally, but returns before leaving its home habitat. */
public final class AnimalHabitatGoal extends Goal {
  public static final String PREFIX = "reterra_home_";
  private final AnimalEntity animal;
  private final BlockPos home;

  private AnimalHabitatGoal(AnimalEntity animal, BlockPos home) {
    this.animal = animal;
    this.home = home;
    setControls(EnumSet.of(Control.MOVE));
  }

  public static void attach(AnimalEntity animal) {
    for (String tag : animal.getCommandTags()) if (tag.startsWith(PREFIX)) {
      try {
        BlockPos home = BlockPos.fromLong(Long.parseLong(tag.substring(PREFIX.length())));
        animal.setPositionTarget(home, 18);
        ((MobGoalsAccessor) animal).reterra$getGoals().add(0, new AnimalHabitatGoal(animal, home));
      } catch (NumberFormatException ignored) { }
      return;
    }
  }

  public static void adoptNearby(ServerWorld world, RestorationState.Machine m) {
    if (!m.kind.equals("restoration_core") || !(m.registered || m.reclaimed)) return;
    for (var animal : world.getEntitiesByClass(AnimalEntity.class, RestorationEngine.bounds(m),
        a -> a.getCommandTags().contains("reterra_revived")
            && a.getCommandTags().stream().noneMatch(t -> t.startsWith(PREFIX)))) {
      animal.addCommandTag(PREFIX + m.pos.asLong());
      attach(animal);
    }
  }

  @Override public boolean canStart() {
    if (animal.isLeashed() || animal.hasVehicle()) return false;
    int x = home.getX() >> 4, z = home.getZ() >> 4;
    return animal.isAlive() && (animal.getX() < x * 16 - 12 || animal.getX() > x * 16 + 28
        || animal.getZ() < z * 16 - 12 || animal.getZ() > z * 16 + 28);
  }

  @Override public boolean shouldContinue() { return canStart(); }

  @Override public void tick() {
    if (!(dev.reterra.compat.WorldAccess.entityWorld(animal) instanceof ServerWorld world) || animal.age % 20 != 0) return;
    BlockPos target = RestorationEngine.ground(world, home.getX() + world.random.nextInt(17) - 8,
        home.getZ() + world.random.nextInt(17) - 8);
    if (target != null) animal.getNavigation().startMovingTo(target.getX() + .5, target.getY() + 1, target.getZ() + .5, 1.1);
  }
}
