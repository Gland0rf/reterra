package dev.reterra.ecology;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

/** Shared palettes for habitat care, flower cultivation and passive succession. */
public final class HabitatPlants {
  private HabitatPlants() {}

  public static int variant(String biome) {
    if (biome.contains("cherry")) return 1;
    if (biome.contains("taiga") || biome.contains("grove") && !biome.contains("cherry")) return 2;
    if (biome.contains("jungle")) return 3;
    if (biome.contains("mangrove")) return 4;
    if (biome.contains("dark_forest")) return 5;
    if (biome.contains("birch")) return 6;
    return 0;
  }

  public static List<Block> flowers(Ecology.Family family, int variant) {
    if (variant == 1) return List.of(Blocks.PINK_PETALS);
    if (family == Ecology.Family.SWAMP) return List.of(Blocks.BLUE_ORCHID);
    if (family == Ecology.Family.HIGHLANDS)
      return List.of(Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.DANDELION);
    return List.of(Blocks.DANDELION, Blocks.POPPY, Blocks.OXEYE_DAISY);
  }

  public static List<Block> trees(Ecology.Family family, int variant) {
    return switch (variant) {
      case 1 -> List.of(Blocks.CHERRY_SAPLING);
      case 2 -> List.of(Blocks.SPRUCE_SAPLING);
      case 3 -> List.of(Blocks.JUNGLE_SAPLING);
      case 4 -> List.of(Blocks.MANGROVE_PROPAGULE);
      case 5 -> List.of(Blocks.DARK_OAK_SAPLING, Blocks.OAK_SAPLING);
      case 6 -> List.of(Blocks.BIRCH_SAPLING);
      default ->
          family == Ecology.Family.HIGHLANDS
              ? List.of(Blocks.SPRUCE_SAPLING)
              : family == Ecology.Family.SWAMP
                  ? List.of(Blocks.OAK_SAPLING)
                  : List.of(Blocks.OAK_SAPLING, Blocks.BIRCH_SAPLING);
    };
  }

  public static boolean nativePlant(Ecology.Family family, String biome, BlockState plant) {
    int variant = variant(biome);
    Block block = plant.getBlock();
    if (flowers(family, variant).contains(block) || trees(family, variant).contains(block))
      return true;
    if (family == Ecology.Family.SWAMP)
      return List.of(Blocks.SUGAR_CANE, Blocks.LILY_PAD, Blocks.VINE, Blocks.BROWN_MUSHROOM)
          .contains(block);
    return family == Ecology.Family.FOREST
        && variant != 1
        && List.of(
                Blocks.FERN,
                Blocks.BROWN_MUSHROOM,
                Blocks.RED_MUSHROOM,
                Blocks.ROSE_BUSH,
                Blocks.LILAC,
                Blocks.PEONY)
            .contains(block);
  }

  public static String careHint(Ecology.Family family, int variant) {
    if (variant == 1) return "plant cherry saplings or pink petals";
    if (family == Ecology.Family.SWAMP) return "plant blue orchids or waterside cane";
    if (variant == 2 || family == Ecology.Family.HIGHLANDS)
      return "plant spruce saplings or oxeye daisies";
    if (variant == 3) return "plant jungle saplings or poppies";
    if (variant == 5) return "plant dark oak saplings or poppies";
    if (variant == 6) return "plant birch saplings or dandelions";
    return "plant oak saplings or dandelions";
  }
}
