package dev.reterra.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.math.BlockPos;

/** Recover the central chunks first; nearby ground positions stay together within each chunk. */
public final class RecoveryPattern {
  private static final Map<Integer, List<int[]>> RINGS = new HashMap<>();

  private RecoveryPattern() {}

  public static BlockPos column(BlockPos center, int radius, int cursor) {
    List<int[]> chunks =
        RINGS.computeIfAbsent(
            radius,
            r -> {
              List<int[]> result = new ArrayList<>();
              for (int x = -r; x <= r; x++)
                for (int z = -r; z <= r; z++) result.add(new int[] {x, z});
              result.sort(
                  Comparator.<int[]>comparingInt(p -> Math.max(Math.abs(p[0]), Math.abs(p[1])))
                      .thenComparingInt(p -> p[1])
                      .thenComparingInt(p -> p[0]));
              return result;
            });
    int index = Math.floorMod(cursor, chunks.size() * 256);
    int[] chunk = chunks.get(index / 256);
    int local = index % 256, x = 0, z = 0;
    for (int bit = 0; bit < 4; bit++) {
      x |= (local >> (bit * 2) & 1) << bit;
      z |= (local >> (bit * 2 + 1) & 1) << bit;
    }
    return new BlockPos(
        ((center.getX() >> 4) + chunk[0]) * 16 + x, 0, ((center.getZ() >> 4) + chunk[1]) * 16 + z);
  }
}
