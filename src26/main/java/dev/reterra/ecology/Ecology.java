package dev.reterra.ecology;

/** Environment rules kept independent of Minecraft so progression can be tested. */
public final class Ecology {
  private Ecology() {}

  public enum Stage {
    BARREN,
    SOIL,
    GROWTH,
    REWILDING,
    RECLAIMED
  }

  public enum Family {
    PLAINS,
    FOREST,
    SWAMP,
    HIGHLANDS
  }

  public static int canopyTarget(Family family) {
    return goals(family).trees;
  }

  public record Goals(
      int soil,
      int grass,
      int water,
      int crops,
      int trees,
      int flowers,
      int species,
      int treeTypes,
      int wetland,
      int slopes) {}

  public static Goals goals(Family family) {
    return switch (family) {
      case PLAINS -> new Goals(65, 45, 8, 12, 4, 3, 5, 0, 0, 0);
      case FOREST -> new Goals(60, 25, 6, 0, 48, 2, 5, 2, 0, 0);
      case SWAMP -> new Goals(45, 15, 32, 0, 12, 2, 5, 0, 8, 0);
      case HIGHLANDS -> new Goals(50, 25, 4, 4, 2, 5, 4, 0, 0, 12);
    };
  }

  public static Goals goals(Family family, boolean introductory) {
    if (!introductory) return goals(family);
    return switch (family) {
      case PLAINS -> new Goals(35, 15, 4, 4, 4, 2, 3, 0, 0, 0);
      case FOREST -> new Goals(35, 15, 4, 0, 16, 2, 3, 1, 0, 0);
      case SWAMP -> new Goals(35, 15, 12, 0, 4, 2, 3, 0, 4, 0);
      case HIGHLANDS -> new Goals(35, 15, 4, 4, 2, 3, 3, 0, 0, 4);
    };
  }

  public static int stabilityTarget(boolean introductory) {
    return introductory ? 90 : STABILITY_SECONDS;
  }

  public static String benefit(Family family) {
    return switch (family) {
      case PLAINS -> "richer compost";
      case FOREST -> "better fuel yield";
      case SWAMP -> "longer pump reach";
      case HIGHLANDS -> "faster Core restoration";
    };
  }

  public static final int STABILITY_SECONDS = 180;

  public record Survey(
      int sampled,
      int fertile,
      int grass,
      int water,
      int crops,
      int trees,
      int flowers,
      int species,
      int animals,
      int treeTypes,
      int wetland,
      int slopes) {
    public Survey(
        int sampled,
        int fertile,
        int grass,
        int water,
        int crops,
        int trees,
        int flowers,
        int species,
        int animals) {
      this(sampled, fertile, grass, water, crops, trees, flowers, species, animals, 0, 0, 0);
    }

    public int soilPercent() {
      return percent(fertile, sampled);
    }

    public int grassPercent() {
      return percent(grass, sampled);
    }

    public boolean habitat() {
      return habitat(Family.PLAINS);
    }

    public boolean habitat(Family family) {
      return sampled >= 128
          && soilPercent() >= 35
          && grassPercent() >= 15
          && water >= 4
          && crops >= Math.min(4, goals(family).crops)
          && species >= 3;
    }

    public Stage stage(Family family) {
      return stage(family, false);
    }

    public Stage stage(Family family, boolean introductory) {
      Goals g = goals(family, introductory);
      if (sampled < 128 || soilPercent() < 35) return Stage.BARREN;
      if (!habitat(family)) return Stage.SOIL;
      if (species < g.species
          || trees < g.trees
          || flowers < g.flowers
          || treeTypes < g.treeTypes
          || wetland < g.wetland
          || slopes < g.slopes) return Stage.GROWTH;
      if (animals < 2
          || grassPercent() < g.grass
          || soilPercent() < g.soil
          || crops < g.crops
          || water < g.water) return Stage.REWILDING;
      return Stage.RECLAIMED;
    }
  }

  public static int percent(int value, int total) {
    return total == 0 ? 0 : value * 100 / total;
  }

  public static int chunk(int coordinate) {
    return Math.floorDiv(coordinate, 16);
  }

  public static boolean inRegion(int centerX, int centerZ, int x, int z) {
    return Math.abs((long) chunk(x) - centerX) <= 1 && Math.abs((long) chunk(z) - centerZ) <= 1;
  }

  public static boolean overlaps(int ax, int az, int bx, int bz) {
    return Math.abs((long) ax - bx) <= 2 && Math.abs((long) az - bz) <= 2;
  }

  public static int scarHash(int x, int z) {
    int h = x * 0x1f1f1f1f ^ z * 0x6c8e9cf5;
    h ^= h >>> 16;
    return h & Integer.MAX_VALUE;
  }

  public static boolean isScar(int x, int z) {
    return scarHash(x, z) % 13 == 0;
  }
}
