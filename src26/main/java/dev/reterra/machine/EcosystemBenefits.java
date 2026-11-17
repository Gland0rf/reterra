package dev.reterra.machine;

import dev.reterra.ecology.Ecology.Family;
import dev.reterra.state.RestorationState;

/** Shared research remains unlocked after a reclaimed machine has been removed. */
public final class EcosystemBenefits {
  private EcosystemBenefits() {}

  public static int compost(int mask) {
    return supplyYield(Balance.COMPOST_WORK, mask, Family.PLAINS);
  }

  public static int fuel(int mask, boolean bio) {
    return supplyYield(bio ? Balance.BIOFUEL_WORK : Balance.COAL_WORK, mask, Family.FOREST);
  }

  public static int culture(int mask) {
    return Balance.CULTURE_WORK + (mask == 0 ? 0 : Balance.CULTURE_WORK / 4);
  }

  private static int supplyYield(int base, int mask, Family special) {
    return base
        + (mask == 0 ? 0 : base / 4)
        + ((mask & 1 << special.ordinal()) == 0 ? 0 : base / 4);
  }

  public static int mask(RestorationState state) {
    return state.completed.stream().mapToInt(f -> 1 << f.ordinal()).reduce(0, (a, b) -> a | b);
  }

  public static int pumpReach(int mask) {
    return (mask & 1 << Family.SWAMP.ordinal()) == 0 ? 8 : 16;
  }

  public static int coreWrites(int mask) {
    return (mask & 1 << Family.HIGHLANDS.ordinal()) == 0 ? 4 : 6;
  }
}
