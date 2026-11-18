package dev.reterra.world;

import dev.reterra.ecology.Ecology;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Runs once during chunk generation. Existing chunks are never stripped on load. */
public final class BarrenWorld {
  private BarrenWorld() {}

  public static boolean vegetation(BlockState s) {
    Block b = s.getBlock();
    return s.is(BlockTags.LEAVES)
        || s.is(BlockTags.FLOWERS)
        || dev.reterra.compat.GameAccess.sapling(s)
        || b instanceof VegetationBlock
        || b instanceof GrowingPlantBlock
        || b instanceof BambooStalkBlock
        || b instanceof CocoaBlock
        || b instanceof VineBlock
        || b instanceof CaveVinesBlock
        || b instanceof CaveVinesPlantBlock
        || b instanceof CactusBlock
        || b instanceof SugarCaneBlock
        || b == Blocks.BEE_NEST
        || b == Blocks.MELON
        || b == Blocks.PUMPKIN
        || b == Blocks.MOSS_CARPET;
  }

  private static boolean affected(BlockState s) {
    return vegetation(s)
        || s.is(BlockTags.DIRT)
        || s.is(Blocks.FARMLAND)
        || s.is(Blocks.DIRT_PATH)
        || s.is(BlockTags.PLANKS)
        || s.is(Blocks.GRASS_BLOCK)
        || s.is(Blocks.PODZOL)
        || s.is(Blocks.MYCELIUM)
        || s.is(Blocks.MOSS_BLOCK)
        || s.is(Blocks.ICE)
        || s.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
  }

  public static boolean lostToCatastrophe(BlockPos pos, BlockState s) {
    return vegetation(s)
        || (pos.getY() >= 55
            && s.is(BlockTags.PLANKS)
            && Ecology.scarHash(pos.getX() ^ (pos.getY() * 31), pos.getZ()) % 41 == 0);
  }

  /** Leave deep remnant water level across columns; never punch random holes in a river. */
  public static BlockState barrenState(BlockPos pos, BlockState original, boolean river, int waterline) {
    boolean drained = river && pos.getY() > waterline;
    if (original.is(Blocks.WATER) || original.is(Blocks.ICE))
      return drained ? Blocks.AIR.defaultBlockState() : original;
    if (lostToCatastrophe(pos, original)) {
      boolean submerged = original.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
      return submerged && !drained ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }
    if (original.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED))
      return drained ? original.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false) : original;
    if (original.is(BlockTags.PLANKS) || original.is(Blocks.COARSE_DIRT)) return original;
    // Newer dirt tags exclude grass, podzol and mycelium.
    if (original.is(BlockTags.DIRT) || original.is(Blocks.GRASS_BLOCK)
        || original.is(Blocks.PODZOL) || original.is(Blocks.MYCELIUM) || original.is(Blocks.FARMLAND)
        || original.is(Blocks.DIRT_PATH) || original.is(Blocks.MOSS_BLOCK))
      return (Ecology.isScar(pos.getX(), pos.getZ()) ? Blocks.GRAVEL : Blocks.COARSE_DIRT)
          .defaultBlockState();
    return original;
  }

  public static void transform(WorldGenLevel world, ChunkAccess chunk) {
    if (!world.getLevel().dimension().equals(Level.OVERWORLD)
        || !WorldOptions.barren(world.getLevel())) return;
    int startX = chunk.getPos().getMinBlockX(), startZ = chunk.getPos().getMinBlockZ();
    int riverWaterline = world.getLevel().getSeaLevel() - 9;
    BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
    LevelChunkSection[] sections = chunk.getSections();
    for (int section = 0; section < sections.length; section++) {
      if (!sections[section].maybeHas(BarrenWorld::affected)) continue;
      int baseY = chunk.getSectionYFromSectionIndex(section) * 16;
      for (int x = 0; x < 16; x++)
        for (int z = 0; z < 16; z++) {
          p.set(startX + x, 64, startZ + z);
          String biome = world.getBiome(p).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
          boolean river = biome.equals("river") || biome.equals("frozen_river");
          for (int y = 0; y < 16; y++) {
            BlockState s = sections[section].getBlockState(x, y, z);
            if (!affected(s)) continue;
            p.set(startX + x, baseY + y, startZ + z);
            BlockState target = barrenState(p, s, river, riverWaterline);
            if (!target.equals(s)) chunk.setBlockState(p, target, 0);
          }
        }
    }
    ResearchSites.maybeGenerate(world, chunk);
  }

  public static Ecology.Family family(String biome) {
    if (biome.contains("swamp")) return Ecology.Family.SWAMP;
    if (biome.contains("peak")
        || biome.contains("mountain")
        || biome.contains("hill")
        || biome.contains("slope")
        || biome.contains("meadow")) return Ecology.Family.HIGHLANDS;
    if (biome.contains("forest")
        || biome.contains("taiga")
        || biome.contains("jungle")
        || biome.contains("grove")) return Ecology.Family.FOREST;
    return Ecology.Family.PLAINS;
  }
}
