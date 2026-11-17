package dev.reterra.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;

public final class Multiblocks {
  private Multiblocks() {}

  public record Part(net.minecraft.world.level.block.Block block, int x, int y, int z, String description) {
    public boolean matches(ServerLevel w, BlockPos anchor) {
      BlockPos p = anchor.offset(x, y, z);
      if (!RestorationEngine.loaded(w, p)) return false;
      return block == Blocks.GLASS
          ? w.getBlockState(p).is(BlockTags.IMPERMEABLE)
          : w.getBlockState(p).is(block);
    }
  }

  public static java.util.List<Part> parts(String kind) {
    var parts = new java.util.ArrayList<Part>();
    switch (kind) {
      case "soil_reclaimer" -> {
        parts.add(new Part(Blocks.FURNACE, 0, -1, 0, "Furnace directly below: heat source"));
        parts.add(new Part(Blocks.BARREL, 0, 0, -1, "Barrel north: bulk gravel storage"));
        parts.add(
            new Part(Blocks.WATER_CAULDRON, 0, 0, 1, "Water cauldron south: washing reservoir"));
      }
      case "restoration_core" -> {
        parts.add(new Part(Blocks.FURNACE, 0, -1, 0, "Furnace below"));
        sides(parts, Blocks.POLISHED_ANDESITE, "Polished andesite");
        parts.add(
            new Part(
                Blocks.WATER_CAULDRON, 0, 0, -2, "Water cauldron two north: irrigation reservoir"));
      }
      case "water_restorer" -> {
        parts.add(
            new Part(
                dev.reterra.compat.GameAccess.COPPER_BLOCK, 0, -1, 0, "Copper block directly below: pump foundation"));
        parts.add(
            new Part(Blocks.WATER_CAULDRON, 0, 0, -1, "Water cauldron north: intake reservoir"));
      }
      case "revival_chamber" -> {
        parts.add(
            new Part(Blocks.IRON_BLOCK, 0, -1, 0, "Iron block directly below: chamber foundation"));
        sides(parts, Blocks.GLASS, "Glass");
        parts.add(
            new Part(
                Blocks.WATER_CAULDRON, 0, 0, -2, "Water cauldron two north: culture reservoir"));
      }
      case "heart_of_the_world" -> {
        parts.add(
            new Part(
                Blocks.DIAMOND_BLOCK,
                0,
                -1,
                0,
                "Diamond block directly below: crystal foundation"));
        sides(parts, Blocks.CHISELED_STONE_BRICKS, "Chiseled stone bricks");
      }
    }
    return parts;
  }

  private static void sides(java.util.List<Part> p, net.minecraft.world.level.block.Block b, String name) {
    p.add(new Part(b, 0, 0, -1, name + " directly north"));
    p.add(new Part(b, 1, 0, 0, name + " directly east"));
    p.add(new Part(b, 0, 0, 1, name + " directly south"));
    p.add(new Part(b, -1, 0, 0, name + " directly west"));
  }

  public static int issue(ServerLevel w, BlockPos pos, String kind) {
    var parts = parts(kind, rotation(w, pos, kind));
    for (int i = 0; i < parts.size(); i++) if (!parts.get(i).matches(w, pos)) return i + 1;
    return 0;
  }

  public static java.util.List<Part> parts(String kind, int rotation) {
    return parts(kind).stream()
        .map(
            p -> {
              int x = p.x(), z = p.z();
              for (int i = 0; i < Math.floorMod(rotation, 4); i++) {
                int next = -z;
                z = x;
                x = next;
              }
              return new Part(
                  p.block(),
                  x,
                  p.y(),
                  z,
                  p.y() < 0
                      ? "Below: " + p.block().getName().getString()
                      : p.block().getName().getString());
            })
        .toList();
  }

  public static int rotation(ServerLevel w, BlockPos pos, String kind) {
    int best = 0, missing = Integer.MAX_VALUE;
    for (int r = 0; r < 4; r++) {
      int count = 0;
      for (var part : parts(kind, r)) if (!part.matches(w, pos)) count++;
      if (count < missing) {
        best = r;
        missing = count;
      }
      if (count == 0) return r;
    }
    return best;
  }

  public static BlockPos barrel(ServerLevel w, BlockPos pos) {
    int r = rotation(w, pos, "soil_reclaimer");
    var part = parts("soil_reclaimer", r).get(1);
    return pos.offset(part.x(), part.y(), part.z());
  }

  public static String missing(ServerLevel world, BlockPos pos) {
    String kind =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(world.getBlockState(pos).getBlock())
            .getPath();
    int problem = issue(world, pos, kind);
    return problem == 0
        ? null
        : parts(kind, rotation(world, pos, kind)).get(problem - 1).description();
  }
}
