package dev.reterra.compat;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Keeps the existing save format and defaults across the NBT API change. */
public final class NbtAccess {
  private NbtAccess() {}

  public static int integer(CompoundTag n, String key) {
    return n.getIntOr(key, 0);
  }

  public static long longValue(CompoundTag n, String key) {
    return n.getLongOr(key, 0L);
  }

  public static boolean bool(CompoundTag n, String key) {
    return n.getBooleanOr(key, false);
  }

  public static String string(CompoundTag n, String key) {
    return n.getStringOr(key, "");
  }

  public static int[] ints(CompoundTag n, String key) {
    return n.getIntArray(key).orElseGet(() -> new int[0]);
  }

  public static long[] longs(CompoundTag n, String key) {
    return n.getLongArray(key).orElseGet(() -> new long[0]);
  }

  public static ListTag compounds(CompoundTag n, String key) {
    return n.getListOrEmpty(key);
  }

  public static CompoundTag compound(ListTag list, int index) {
    return list.getCompoundOrEmpty(index);
  }

  public static void writeItems(CompoundTag n,
      net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items,
      com.mojang.serialization.DynamicOps<Tag> ops) {
    ListTag list = new ListTag();
    for (int i = 0; i < items.size(); i++) {
      var stack = items.get(i);
      if (stack.isEmpty()) continue;
      int slot = i;
      net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops, stack)
          .resultOrPartial(org.slf4j.LoggerFactory.getLogger("ReTerra")::error)
          .ifPresent(value -> {
            CompoundTag item = (CompoundTag) value;
            item.putByte("Slot", (byte) slot);
            list.add(item);
          });
    }
    n.put("Items", list);
  }

  public static void readItems(CompoundTag n,
      net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items,
      com.mojang.serialization.DynamicOps<Tag> ops) {
    for (Tag element : compounds(n, "Items")) {
      if (!(element instanceof CompoundTag item)) continue;
      int slot = integer(item, "Slot") & 255;
      if (slot >= items.size()) continue;
      net.minecraft.world.item.ItemStack.CODEC.parse(ops, item)
          .resultOrPartial(org.slf4j.LoggerFactory.getLogger("ReTerra")::error)
          .ifPresent(stack -> items.set(slot, stack));
    }
  }

  public static <S> com.mojang.serialization.Codec<S> stateCodec(
      java.util.function.BiFunction<CompoundTag, com.mojang.serialization.DynamicOps<Tag>, S> read,
      java.util.function.BiFunction<S, com.mojang.serialization.DynamicOps<Tag>, CompoundTag> write) {
    return new com.mojang.serialization.Codec<>() {
      private com.mojang.serialization.DynamicOps<Tag> nbtOps(
          com.mojang.serialization.DynamicOps<?> ops) {
        return ops instanceof net.minecraft.resources.RegistryOps<?> registryOps
            ? registryOps.withParent(net.minecraft.nbt.NbtOps.INSTANCE) : net.minecraft.nbt.NbtOps.INSTANCE;
      }

      @Override
      public <T> com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<S, T>> decode(
          com.mojang.serialization.DynamicOps<T> ops, T input) {
        return CompoundTag.CODEC.decode(ops, input)
            .map(pair -> com.mojang.datafixers.util.Pair.of(read.apply(pair.getFirst(), nbtOps(ops)), pair.getSecond()));
      }

      @Override
      public <T> com.mojang.serialization.DataResult<T> encode(S state,
          com.mojang.serialization.DynamicOps<T> ops, T prefix) {
        return CompoundTag.CODEC.encode(write.apply(state, nbtOps(ops)), ops, prefix);
      }
    };
  }

  public static void putUuid(CompoundTag n, String key, UUID id) {
    long most = id.getMostSignificantBits(), least = id.getLeastSignificantBits();
    n.putIntArray(key, new int[] {(int) (most >> 32), (int) most, (int) (least >> 32), (int) least});
  }

  public static boolean hasUuid(CompoundTag n, String key) {
    return ints(n, key).length == 4;
  }

  public static UUID uuid(CompoundTag n, String key) {
    int[] a = ints(n, key);
    if (a.length != 4) throw new IllegalArgumentException("Invalid UUID: " + key);
    return new UUID(((long) a[0] << 32) | (a[1] & 0xFFFFFFFFL),
        ((long) a[2] << 32) | (a[3] & 0xFFFFFFFFL));
  }
}
