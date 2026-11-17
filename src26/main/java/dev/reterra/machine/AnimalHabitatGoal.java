package dev.reterra.machine;

import dev.reterra.mixin.MobGoalsAccessor;
import dev.reterra.state.RestorationState;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;

/** Revived wildlife wanders normally, but returns before leaving its home habitat. */
public final class AnimalHabitatGoal extends Goal {
  public static final String PREFIX = "reterra_home_";
  private final Animal animal;
  private final BlockPos home;

  private AnimalHabitatGoal(Animal animal, BlockPos home) {
    this.animal = animal;
    this.home = home;
    setFlags(EnumSet.of(Flag.MOVE));
  }

  public static void attach(Animal animal) {
    for (String tag : animal.entityTags()) if (tag.startsWith(PREFIX)) {
      try {
        BlockPos home = BlockPos.of(Long.parseLong(tag.substring(PREFIX.length())));
        animal.setHomeTo(home, 18);
        ((MobGoalsAccessor) animal).reterra$getGoals().addGoal(0, new AnimalHabitatGoal(animal, home));
      } catch (NumberFormatException ignored) { }
      return;
    }
  }

  public static void adoptNearby(ServerLevel world, RestorationState.Machine m) {
    if (!m.kind.equals("restoration_core") || !(m.registered || m.reclaimed)) return;
    for (var animal : world.getEntitiesOfClass(Animal.class, RestorationEngine.bounds(m),
        a -> a.entityTags().contains("reterra_revived")
            && a.entityTags().stream().noneMatch(t -> t.startsWith(PREFIX)))) {
      animal.addTag(PREFIX + m.pos.asLong());
      attach(animal);
    }
  }

  @Override public boolean canUse() {
    if (animal.isLeashed() || animal.isPassenger()) return false;
    int x = home.getX() >> 4, z = home.getZ() >> 4;
    return animal.isAlive() && (animal.getX() < x * 16 - 12 || animal.getX() > x * 16 + 28
        || animal.getZ() < z * 16 - 12 || animal.getZ() > z * 16 + 28);
  }

  @Override public boolean canContinueToUse() { return canUse(); }

  @Override public void tick() {
    if (!(dev.reterra.compat.WorldAccess.entityWorld(animal) instanceof ServerLevel world) || animal.tickCount % 20 != 0) return;
    BlockPos target = RestorationEngine.ground(world, home.getX() + world.getRandom().nextInt(17) - 8,
        home.getZ() + world.getRandom().nextInt(17) - 8);
    if (target != null) animal.getNavigation().moveTo(target.getX() + .5, target.getY() + 1, target.getZ() + .5, 1.1);
  }
}
