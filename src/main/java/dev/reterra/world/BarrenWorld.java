package dev.reterra.world;

import dev.reterra.ecology.Ecology;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

/** Runs once during chunk generation. Existing chunks are never stripped on load. */
public final class BarrenWorld {
  private BarrenWorld() {}

  public static boolean vegetation(BlockState s) {
    Block b = s.getBlock();
    return s.isIn(BlockTags.LEAVES)
        || s.isIn(BlockTags.FLOWERS)
        || s.isIn(BlockTags.SAPLINGS)
        || b instanceof PlantBlock
        || b instanceof AbstractPlantPartBlock
        || b instanceof BambooBlock
        || b instanceof CocoaBlock
        || b instanceof VineBlock
        || b instanceof CaveVinesHeadBlock
        || b instanceof CaveVinesBodyBlock
        || b instanceof CactusBlock
        || b instanceof SugarCaneBlock
        || b == Blocks.BEE_NEST
        || b == Blocks.MELON
        || b == Blocks.PUMPKIN
        || b == Blocks.MOSS_CARPET;
  }

  private static boolean affected(BlockState s) {
    return vegetation(s)
        || s.isIn(BlockTags.DIRT)
        || s.isOf(Blocks.FARMLAND)
        || s.isOf(Blocks.DIRT_PATH)
        || s.isIn(BlockTags.PLANKS)
        || s.isOf(Blocks.GRASS_BLOCK)
        || s.isOf(Blocks.PODZOL)
        || s.isOf(Blocks.MYCELIUM)
        || s.isOf(Blocks.MOSS_BLOCK)
        || s.isOf(Blocks.ICE)
        || s.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER);
  }

  public static boolean lostToCatastrophe(BlockPos pos, BlockState s) {
    return vegetation(s)
        || (pos.getY() >= 55
            && s.isIn(BlockTags.PLANKS)
            && Ecology.scarHash(pos.getX() ^ (pos.getY() * 31), pos.getZ()) % 41 == 0);
  }

  /** Leave deep remnant water level across columns; never punch random holes in a river. */
  public static BlockState barrenState(BlockPos pos, BlockState original, boolean river, int waterline) {
    boolean drained = river && pos.getY() > waterline;
    if (original.isOf(Blocks.WATER) || original.isOf(Blocks.ICE))
      return drained ? Blocks.AIR.getDefaultState() : original;
    if (lostToCatastrophe(pos, original)) {
      boolean submerged = original.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER);
      return submerged && !drained ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
    }
    if (original.contains(net.minecraft.state.property.Properties.WATERLOGGED))
      return drained ? original.with(net.minecraft.state.property.Properties.WATERLOGGED, false) : original;
    if (original.isIn(BlockTags.PLANKS) || original.isOf(Blocks.COARSE_DIRT)) return original;
    if (original.isIn(BlockTags.DIRT) || original.isOf(Blocks.FARMLAND)
        || original.isOf(Blocks.DIRT_PATH) || original.isOf(Blocks.MOSS_BLOCK))
      return (Ecology.isScar(pos.getX(), pos.getZ()) ? Blocks.GRAVEL : Blocks.COARSE_DIRT)
          .getDefaultState();
    return original;
  }

  public static void transform(StructureWorldAccess world, Chunk chunk) {
    if (!world.toServerWorld().getRegistryKey().equals(World.OVERWORLD)
        || !WorldOptions.barren(world.toServerWorld())) return;
    int startX = chunk.getPos().getStartX(), startZ = chunk.getPos().getStartZ();
    int riverWaterline = world.toServerWorld().getSeaLevel() - 9;
    BlockPos.Mutable p = new BlockPos.Mutable();
    ChunkSection[] sections = chunk.getSectionArray();
    for (int section = 0; section < sections.length; section++) {
      if (!sections[section].hasAny(BarrenWorld::affected)) continue;
      int baseY = chunk.sectionIndexToCoord(section) * 16;
      for (int x = 0; x < 16; x++)
        for (int z = 0; z < 16; z++) {
          p.set(startX + x, 64, startZ + z);
          String biome = world.getBiome(p).getKey().map(k -> k.getValue().getPath()).orElse("");
          boolean river = biome.equals("river") || biome.equals("frozen_river");
          for (int y = 0; y < 16; y++) {
            BlockState s = sections[section].getBlockState(x, y, z);
            if (!affected(s)) continue;
            p.set(startX + x, baseY + y, startZ + z);
            BlockState target = barrenState(p, s, river, riverWaterline);
            //? if <1.21.5 {
            if (!target.equals(s)) chunk.setBlockState(p, target, false);
            //? } else {
            /*if (!target.equals(s)) chunk.setBlockState(p, target, 0);
            *///? }
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
