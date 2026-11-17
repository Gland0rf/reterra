package dev.reterra.world;

import dev.reterra.Content;
import dev.reterra.state.RestorationState.Machine;
import java.util.Locale;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.FillBiomeCommand;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Vanilla's biome editor handles palette updates, persistence and client packets. */
public final class ReclaimedBiomes {
  private ReclaimedBiomes() {}

  public static String id(Machine m) {
    return "reterra:reclaimed_" + m.family.name().toLowerCase(Locale.ROOT);
  }

  public static void tick(ServerWorld world, Machine m) {
    int radius = m.spreadRadius, side = radius * 2 + 1;
    int sector = Math.floorMod(m.biomeCursor++, side * side);
    int cx = (m.pos.getX() >> 4) + sector % side - radius, cz = (m.pos.getZ() >> 4) + sector / side - radius;
    long chunkKey = net.minecraft.util.math.ChunkPos.toLong(cx, cz);
    if (m.biomeChunks.contains(chunkKey)) return;
    var owner = dev.reterra.machine.RestorationEngine.regionAt(dev.reterra.state.RestorationState.get(world), new BlockPos(cx * 16 + 8, 64, cz * 16 + 8));
    if (owner != null && owner != m) return;
    if (!world.getChunkManager().isChunkLoaded(cx, cz)) return;
    //? if <1.21.2 {
    var registry = world.getRegistryManager().get(RegistryKeys.BIOME);
    //? } else {
    /*var registry = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
    *///? }
    var target =
        registry
            .getEntry(Content.id("reclaimed_" + m.family.name().toLowerCase(Locale.ROOT)))
            .orElseThrow();
    boolean success = true;
    // Small vertical strips obey the vanilla modification limit; no adjacent chunks are requested.
    for (int y = world.getBottomY(); y < (world.getBottomY() + world.getHeight()); y += 32) {
      var result =
          FillBiomeCommand.fillBiome(
              world,
              new BlockPos(cx * 16, y, cz * 16),
              new BlockPos(cx * 16 + 15, Math.min(y + 31, (world.getBottomY() + world.getHeight()) - 1), cz * 16 + 15),
              target,
              entry ->
                  entry
                      .getKey()
                      .map(key -> key.getValue().toString().equals(m.biome))
                      .orElse(false),
              feedback -> {});
      success &= result.left().isPresent();
    }
    if (success) {
      m.biomeChunks.add(chunkKey);
      int dx = cx - (m.pos.getX() >> 4), dz = cz - (m.pos.getZ() >> 4);
      if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) m.sectors[(dz + 1) * 3 + dx + 1].biomeTransitioned = true;
    }
  }
}
