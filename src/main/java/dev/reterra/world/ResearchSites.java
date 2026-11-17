package dev.reterra.world;

import dev.reterra.Content;
import dev.reterra.ecology.Ecology;
import net.minecraft.block.*;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;

/**
 * Six authored building plans. Terrain fit and purpose determine placement, not a random box
 * palette.
 */
public final class ResearchSites {
  private ResearchSites() {}

  public enum Kind {
    FARMHOUSE,
    GREENHOUSE,
    SEED_VAULT,
    WATERWORKS,
    BIOLOGICAL_ARCHIVE,
    RESEARCH_STATION
  }

  public static BlockPos candidate(long seed, int cellX, int cellZ) {
    int h = Ecology.scarHash(cellX ^ (int) seed, cellZ ^ (int) (seed >>> 32));
    return new BlockPos(
        (cellX * 8 + 2 + h % 4) * 16 + 8, 0, (cellZ * 8 + 2 + (h >>> 3) % 4) * 16 + 8);
  }

  public static Kind kind(long seed, int cellX, int cellZ) {
    int h = Ecology.scarHash(cellX ^ (int) seed, cellZ ^ (int) (seed >>> 32));
    return switch (Math.floorMod(cellX + cellZ * 3, 8)) {
      case 0, 3, 6 -> Kind.FARMHOUSE;
      case 1, 4 -> Kind.GREENHOUSE;
      case 2 -> Kind.WATERWORKS;
      case 5 -> Kind.SEED_VAULT;
      default -> ((h >>> 6) & 1) == 0 ? Kind.BIOLOGICAL_ARCHIVE : Kind.RESEARCH_STATION;
    };
  }

  public static void maybeGenerate(StructureWorldAccess w, Chunk chunk) {
    int cx = chunk.getPos().x, cz = chunk.getPos().z;
    int cellX = Math.floorDiv(cx, 8), cellZ = Math.floorDiv(cz, 8);
    BlockPos candidate = candidate(w.getSeed(), cellX, cellZ);
    if (cx != candidate.getX() >> 4 || cz != candidate.getZ() >> 4) return;
    BlockPos p = siteGround(chunk);
    if (p == null || !fits(w, chunk, p)) return;
    Kind kind = kind(w.getSeed(), cellX, cellZ);
    String biome = w.getBiome(p).getKey().orElseThrow().getValue().getPath();
    // Pumps belong to watercourses; archives and workshops need stable dry foundations.
    if (kind == Kind.WATERWORKS
        && !biome.contains("river")
        && !biome.contains("swamp")
        && p.getY() > 64) kind = Kind.FARMHOUSE;
    build(w, p, kind);
  }

  private static BlockPos siteGround(Chunk chunk) {
    int x = chunk.getPos().getStartX() + 8, z = chunk.getPos().getStartZ() + 8;
    int y = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, 8, 8) + 1;
    for (int i = 0;
        i < 24 && chunk.getBlockState(new BlockPos(x, y - 1, z)).isIn(BlockTags.LOGS);
        i++) y--;
    if (y < 55
        || y > (chunk.getBottomY() + chunk.getHeight()) - 9
        || !chunk.getBlockState(new BlockPos(x, y - 1, z)).getFluidState().isEmpty()) return null;
    return new BlockPos(x, y, z);
  }

  private static boolean fits(StructureWorldAccess w, Chunk chunk, BlockPos p) {
    for (int dx : new int[] {-6, 0, 6})
      for (int dz : new int[] {-5, 0, 5}) {
        int top =
            chunk.sampleHeightmap(
                    Heightmap.Type.OCEAN_FLOOR, (p.getX() + dx) & 15, (p.getZ() + dz) & 15)
                + 1;
        if (Math.abs(top - p.getY()) > 4) return false;
      }
    for (int x = -6; x <= 6; x++)
      for (int z = -5; z <= 5; z++)
        for (int y = 0; y <= 6; y++) {
          var state = chunk.getBlockState(p.add(x, y, z));
          if (state.isIn(BlockTags.PLANKS) || w.getBlockEntity(p.add(x, y, z)) != null)
            return false;
        }
    return true;
  }

  /**
   * A fresh world's first expedition has a nearby physical seed source; nothing enters inventory.
   */
  public static boolean ensureFirstSite(ServerWorld w, BlockPos spawn) {
    var state = dev.reterra.state.RestorationState.get(w);
    for (int radius = 2; radius <= 10; radius++)
      for (int dx = -radius; dx <= radius; dx++)
        for (int dz = -radius; dz <= radius; dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
          int cx = (spawn.getX() >> 4) + dx, cz = (spawn.getZ() >> 4) + dz;
          if (!w.getChunkManager().isChunkLoaded(cx, cz)) continue;
          if (state.protectedBlocks.stream()
              .map(BlockPos::fromLong)
              .anyMatch(p -> (p.getX() >> 4) == cx && (p.getZ() >> 4) == cz)) continue;
          Chunk chunk = w.getChunk(cx, cz);
          BlockPos p = siteGround(chunk);
          if (p != null && fits(w, chunk, p)) {
            build(w, p, Kind.FARMHOUSE);
            state.firstSite = p;
            state.firstSitePending = false;
            state.markDirty();
            return true;
          }
        }
    return false;
  }

  /** Retry after chunks finish loading, regardless of world age or first-login timing. */
  public static void tickFirstSite(ServerWorld world) {
    var state = dev.reterra.state.RestorationState.get(world);
    if (!state.firstSitePending || world.getPlayers().isEmpty() || !WorldOptions.barren(world))
      return;
    state.firstSiteCursor++;
    if (state.firstSiteCursor % 3 != 1) return;
    // Reuse a generated nearby farmhouse before placing another one.
    for (var entry : state.knownSites.entrySet()) {
      BlockPos p = BlockPos.fromLong(entry.getKey());
      if (entry.getValue().equals(Kind.FARMHOUSE.name())
          && p.getSquaredDistance(dev.reterra.compat.WorldAccess.spawn(world)) < 192 * 192) {
        state.firstSite = p;
        state.firstSitePending = false;
        state.markDirty();
        return;
      }
    }
    ensureFirstSite(world, dev.reterra.compat.WorldAccess.spawn(world));
  }

  public static BlockPos chestPosition(BlockPos surface, Kind kind) {
    return switch (kind) {
      case FARMHOUSE -> surface.add(-3, 0, 2);
      case GREENHOUSE -> surface.add(0, 0, -3);
      case SEED_VAULT -> surface.add(4, -9, 2);
      case WATERWORKS -> surface.add(4, 0, 2);
      case BIOLOGICAL_ARCHIVE -> surface.add(4, 0, 2);
      case RESEARCH_STATION -> surface.add(4, 0, -2);
    };
  }

  public static void build(StructureWorldAccess w, BlockPos surface, Kind kind) {
    Plan p = new Plan(w, kind == Kind.SEED_VAULT ? surface.down(9) : surface);
    p.foundation(kind == Kind.FARMHOUSE ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS);
    switch (kind) {
      case FARMHOUSE -> farmhouse(p);
      case GREENHOUSE -> greenhouse(p);
      case SEED_VAULT -> vault(p, surface);
      case WATERWORKS -> waterworks(p);
      case BIOLOGICAL_ARCHIVE -> archive(p);
      case RESEARCH_STATION -> station(p);
    }
    p.details(kind);
    BlockPos chest = chestPosition(surface, kind);
    w.setBlockState(chest, Blocks.CHEST.getDefaultState(), Block.NOTIFY_LISTENERS);
    if (w.getBlockEntity(chest) instanceof LootableContainerBlockEntity container)
      container.setLootTable(
          RegistryKey.of(
              RegistryKeys.LOOT_TABLE,
              Content.id("chests/" + kind.name().toLowerCase(java.util.Locale.ROOT))),
          w.getSeed() ^ chest.asLong());
    if (w instanceof ServerWorld server) {
      var state = dev.reterra.state.RestorationState.get(server);
      state.knownSites.put(surface.asLong(), kind.name());
      state.markDirty();
    }
  }

  private static void farmhouse(Plan p) {
    p.room(-5, -4, 1, 4, Blocks.OAK_PLANKS, 3);
    for (int x : new int[] {-5, 1})
      for (int z : new int[] {-4, 4}) for (int y = 0; y <= 3; y++) p.put(x, y, z, Blocks.OAK_LOG);
    p.door(-2, 0, 4);
    p.window(-5, 0);
    p.window(1, 0);
    // A pitched roof, one collapsed rear corner, and a stone chimney make the silhouette readable.
    for (int x = -6; x <= 2; x++)
      for (int z = -5; z <= 5; z++) {
        int y = 4 + (4 - Math.abs(x + 2)) / 2;
        if (x >= 0 && z <= -3) continue;
        if (x == -2) p.put(x, y, z, Blocks.OAK_SLAB);
        else
          p.state(
              x,
              y,
              z,
              Blocks.OAK_STAIRS
                  .getDefaultState()
                  .with(StairsBlock.FACING, x < -2 ? Direction.EAST : Direction.WEST));
      }
    p.put(-4, 0, -2, Blocks.FURNACE);
    for (int y = 1; y <= 6; y++) p.put(-4, y, -2, Blocks.COBBLESTONE);
    p.put(0, 0, -2, Blocks.CRAFTING_TABLE);
    p.put(0, 0, 2, Blocks.COMPOSTER);
    for (int x = 3; x <= 5; x++) for (int z = -3; z <= 3; z++) p.put(x, -1, z, Blocks.COARSE_DIRT);
    p.put(4, -1, 0, Blocks.WATER);
    for (int z = -4; z <= 4; z++) if (z != 0) p.put(6, 0, z, Blocks.OAK_FENCE);
    // The empty irrigated bed and nearby composter demonstrate the first restoration task.
  }

  private static void greenhouse(Plan p) {
    p.room(-5, -4, 5, 4, Blocks.GLASS, 3);
    for (int x : new int[] {-5, 5})
      for (int z : new int[] {-4, 4}) for (int y = 0; y <= 4; y++) p.put(x, y, z, Blocks.IRON_BARS);
    p.door(0, 0, 4);
    for (int x = -5; x <= 5; x++)
      for (int z = -4; z <= 4; z++) {
        if (x > 1 && z < 0) continue;
        p.put(x, 4 + (5 - Math.abs(x)) / 3, z, Blocks.GLASS);
      }
    for (int x : new int[] {-3, 3})
      for (int z = -2; z <= 2; z++) {
        p.put(x, -1, z, Blocks.COARSE_DIRT);
        p.put(x + 1, -1, z, Blocks.BRICKS);
      }
    for (int z = -3; z <= 3; z++) p.put(0, -1, z, Blocks.POLISHED_ANDESITE);
    p.put(-4, 0, 3, Blocks.BEEHIVE);
    p.put(-4, 0, -3, Blocks.COMPOSTER);
    p.put(4, 0, 3, Blocks.WATER_CAULDRON);
    p.put(4, 0, -3, Blocks.FLOWER_POT);
  }

  private static void waterworks(Plan p) {
    for (int x = -5; x <= 0; x++)
      for (int z = -4; z <= 4; z++) {
        boolean edge = x == -5 || x == 0 || Math.abs(z) == 4;
        p.put(x, edge ? 0 : -1, z, edge ? Blocks.STONE_BRICKS : Blocks.GRAVEL);
      }
    for (int x = -3; x <= -2; x++) for (int z = -1; z <= 1; z++) p.put(x, -1, z, Blocks.WATER);
    p.room(2, -3, 5, 3, Blocks.STONE_BRICKS, 3);
    p.door(3, 0, 3);
    for (int x = 1; x <= 6; x++)
      for (int z = -4; z <= 4; z++) if (z < 2) p.put(x, 3, z, Blocks.STONE_BRICK_SLAB);
    p.put(3, -1, 0, Blocks.COPPER_BLOCK);
    p.put(3, 0, 0, Blocks.CUT_COPPER);
    p.put(3, 0, -1, Blocks.WATER_CAULDRON);
    for (int x = -1; x <= 2; x++)
      p.state(
          x,
          0,
          0,
          Blocks.LIGHTNING_ROD.getDefaultState().with(LightningRodBlock.FACING, Direction.EAST));
  }

  private static void archive(Plan p) {
    p.room(-5, -4, 5, 4, Blocks.STONE_BRICKS, 4);
    p.door(0, 0, 4);
    for (int x = -4; x <= 4; x++) for (int z = -3; z <= 3; z++) p.put(x, -1, z, Blocks.CALCITE);
    for (int x : new int[] {-3, 3}) for (int y = 0; y <= 2; y++) p.put(x, y, -2, Blocks.GLASS);
    p.put(0, -1, 0, Blocks.IRON_BLOCK);
    p.put(0, 0, 0, Blocks.CHISELED_STONE_BRICKS);
    p.put(0, 0, -1, Blocks.GLASS);
    p.put(1, 0, 0, Blocks.GLASS);
    p.put(0, 0, 1, Blocks.GLASS);
    p.put(-1, 0, 0, Blocks.GLASS);
    p.put(0, 0, -2, Blocks.WATER_CAULDRON);
    p.put(-4, 0, 2, Blocks.BREWING_STAND);
    for (int x = -5; x <= 5; x++)
      for (int z = -4; z <= 4; z++) if (x < 2 || z < 1) p.put(x, 4, z, Blocks.STONE_BRICK_SLAB);
    p.window(-5, 0);
    p.window(5, 0);
  }

  private static void station(Plan p) {
    p.room(-5, -4, 5, 4, Blocks.STONE_BRICKS, 4);
    p.door(0, 0, 4);
    for (int x = -4; x <= 4; x++) p.put(x, -1, 0, Blocks.POLISHED_DEEPSLATE);
    p.put(0, -1, 0, Blocks.POLISHED_ANDESITE);
    p.put(0, 0, 0, Blocks.CHISELED_STONE_BRICKS);
    p.put(0, 0, -1, Blocks.POLISHED_ANDESITE);
    p.put(1, 0, 0, Blocks.POLISHED_ANDESITE);
    p.put(0, 0, 1, Blocks.POLISHED_ANDESITE);
    p.put(-1, 0, 0, Blocks.POLISHED_ANDESITE);
    p.put(0, 0, -2, Blocks.WATER_CAULDRON);
    p.put(-4, 0, -2, Blocks.LECTERN);
    p.put(-4, 0, 1, Blocks.REDSTONE_LAMP);
    for (int x = -5; x <= 5; x++)
      for (int z = -4; z <= 4; z++) if (x <= 0 || z < 0) p.put(x, 4, z, Blocks.STONE_BRICK_SLAB);
    p.window(-5, 0);
    p.window(5, 0);
  }

  private static void vault(Plan p, BlockPos surface) {
    p.room(-5, -4, 5, 4, Blocks.STONE_BRICKS, 3);
    for (int x = -5; x <= 5; x++)
      for (int z = -4; z <= 4; z++) p.put(x, 3, z, Blocks.CRACKED_STONE_BRICKS);
    for (int x : new int[] {-4, 4})
      for (int z = -2; z <= 0; z++) {
        p.put(x, 0, z, Blocks.BARREL);
        p.put(x, 1, z, Blocks.IRON_BARS);
      }
    for (int y = 0; y <= 9; y++) {
      p.put(0, y, 1, Blocks.STONE_BRICKS);
      p.state(0, y, 0, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.NORTH));
    }
    p.put(-4, 2, 2, Blocks.GLOWSTONE);
    p.put(4, 2, 2, Blocks.GLOWSTONE);
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++)
        if (x != 0 || z != 0) p.put(x, 8, z, Blocks.CRACKED_STONE_BRICKS);
    p.state(0, 10, 0, Blocks.OAK_TRAPDOOR.getDefaultState().with(TrapdoorBlock.OPEN, true));
    p.put(2, 9, 0, Blocks.CHISELED_STONE_BRICKS);
  }

  private static final class Plan {
    private final StructureWorldAccess world;
    private final BlockPos origin;

    Plan(StructureWorldAccess world, BlockPos origin) {
      this.world = world;
      this.origin = origin;
    }

    void state(int x, int y, int z, BlockState state) {
      world.setBlockState(origin.add(x, y, z), state, Block.NOTIFY_LISTENERS);
    }

    void put(int x, int y, int z, Block block) {
      state(x, y, z, block.getDefaultState());
    }

    void foundation(Block material) {
      for (int x = -6; x <= 6; x++)
        for (int z = -5; z <= 5; z++) {
          for (int y = 0; y <= 7; y++) put(x, y, z, Blocks.AIR);
          put(x, -1, z, material);
          for (int depth = 2; depth <= 6; depth++) {
            var below = world.getBlockState(origin.add(x, -depth, z));
            if (!below.isAir() && below.getFluidState().isEmpty()) break;
            put(x, -depth, z, material);
          }
        }
    }

    void room(int minX, int minZ, int maxX, int maxZ, Block wall, int height) {
      for (int x = minX; x <= maxX; x++)
        for (int z = minZ; z <= maxZ; z++)
          if (x == minX || x == maxX || z == minZ || z == maxZ)
            for (int y = 0; y < height; y++) put(x, y, z, wall);
    }

    void door(int x, int y, int z) {
      put(x, y, z, Blocks.AIR);
      put(x, y + 1, z, Blocks.AIR);
    }

    void window(int x, int z) {
      for (int dz = -1; dz <= 1; dz++) put(x, 1, z + dz, Blocks.GLASS_PANE);
    }

    void details(Kind kind) {
      // A consistent base, weathering, lintels and furnishings give each ruin a readable purpose.
      for (int x = -6; x <= 6; x++)
        for (int z = -5; z <= 5; z++) {
          BlockPos p = origin.add(x, -1, z);
          if (world.getBlockState(p).isOf(Blocks.STONE_BRICKS)
              && Ecology.scarHash(p.getX(), p.getZ()) % 5 == 0)
            put(x, -1, z, Blocks.CRACKED_STONE_BRICKS);
        }
      if (kind == Kind.SEED_VAULT) return;
      Block trim = kind == Kind.FARMHOUSE ? Blocks.SPRUCE_SLAB : Blocks.STONE_BRICK_SLAB;
      for (int z : new int[] {-4, 4})
        for (int x : new int[] {-5, 5}) {
          if (kind == Kind.FARMHOUSE && x == 5) continue;
          put(x, 0, z, kind == Kind.FARMHOUSE ? Blocks.COBBLESTONE : Blocks.CHISELED_STONE_BRICKS);
        }
      if (kind == Kind.BIOLOGICAL_ARCHIVE || kind == Kind.RESEARCH_STATION) {
        for (int x = -4; x <= 4; x++) put(x, 3, 4, trim);
        for (int z = -2; z <= 2; z++) put(-4, 0, z, Blocks.POLISHED_ANDESITE_SLAB);
        put(-4, 1, -1, Blocks.FLOWER_POT);
        put(4, 0, 0, Blocks.CARTOGRAPHY_TABLE);
        put(4, 1, 0, Blocks.LANTERN);
        put(-3, 0, 3, Blocks.BOOKSHELF);
        put(-2, 0, 3, Blocks.BOOKSHELF);
      }
      if (kind == Kind.GREENHOUSE) {
        for (int z = -3; z <= 3; z++) put(5, 3, z, Blocks.CUT_COPPER_SLAB);
        put(1, 0, 3, Blocks.CRAFTING_TABLE);
        put(-1, 0, 3, Blocks.LANTERN);
      }
      if (kind == Kind.FARMHOUSE) {
        put(-3, 0, -3, Blocks.BOOKSHELF);
        put(-2, 0, -3, Blocks.SPRUCE_STAIRS);
        put(-3, 1, -3, Blocks.LANTERN);
      }
    }
  }
}
