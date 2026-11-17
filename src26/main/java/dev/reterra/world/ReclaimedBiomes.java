package dev.reterra.world;

import dev.reterra.Content;
import dev.reterra.state.RestorationState.Machine;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;

/** Vanilla's biome editor handles palette updates, persistence and client packets. */
public final class ReclaimedBiomes {
  private ReclaimedBiomes() {}

  public static String id(Machine m) {
    return "reterra:reclaimed_" + m.family.name().toLowerCase(Locale.ROOT);
  }

  public static void tick(ServerLevel world, Machine m) {
    int radius = m.spreadRadius, side = radius * 2 + 1;
    int sector = Math.floorMod(m.biomeCursor++, side * side);
    int cx = (m.pos.getX() >> 4) + sector % side - radius, cz = (m.pos.getZ() >> 4) + sector / side - radius;
    long chunkKey = net.minecraft.world.level.ChunkPos.pack(cx, cz);
    if (m.biomeChunks.contains(chunkKey)) return;
    var owner = dev.reterra.machine.RestorationEngine.regionAt(dev.reterra.state.RestorationState.get(world), new BlockPos(cx * 16 + 8, 64, cz * 16 + 8));
    if (owner != null && owner != m) return;
    if (!world.getChunkSource().hasChunk(cx, cz)) return;
    var registry = world.registryAccess().lookupOrThrow(Registries.BIOME);
    var target =
        registry
            .get(Content.id("reclaimed_" + m.family.name().toLowerCase(Locale.ROOT)))
            .orElseThrow();
    boolean success = true;
    // Small vertical strips obey the vanilla modification limit; no adjacent chunks are requested.
    for (int y = world.getMinY(); y < (world.getMinY() + world.getHeight()); y += 32) {
      var result =
          FillBiomeCommand.fill(
              world,
              new BlockPos(cx * 16, y, cz * 16),
              new BlockPos(cx * 16 + 15, Math.min(y + 31, (world.getMinY() + world.getHeight()) - 1), cz * 16 + 15),
              target,
              entry ->
                  entry
                      .unwrapKey()
                      .map(key -> key.identifier().toString().equals(m.biome))
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
