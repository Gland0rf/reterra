package dev.reterra.compat;

import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/** Keeps the existing save format and defaults across the NBT API change. */
public final class NbtAccess {
  private NbtAccess() {}

  public static int integer(NbtCompound n, String key) {
    //? if <1.21.5 {
    return n.getInt(key);
    //? } else {
    /*return n.getInt(key, 0);
    *///? }
  }

  public static long longValue(NbtCompound n, String key) {
    //? if <1.21.5 {
    return n.getLong(key);
    //? } else {
    /*return n.getLong(key, 0L);
    *///? }
  }

  public static boolean bool(NbtCompound n, String key) {
    //? if <1.21.5 {
    return n.getBoolean(key);
    //? } else {
    /*return n.getBoolean(key, false);
    *///? }
  }

  public static String string(NbtCompound n, String key) {
    //? if <1.21.5 {
    return n.getString(key);
    //? } else {
    /*return n.getString(key, "");
    *///? }
  }

  public static int[] ints(NbtCompound n, String key) {
    //? if <1.21.5 {
    return n.getIntArray(key);
    //? } else {
    /*return n.getIntArray(key).orElseGet(() -> new int[0]);
    *///? }
  }

  public static long[] longs(NbtCompound n, String key) {
    //? if <1.21.5 {
    return n.getLongArray(key);
    //? } else {
    /*return n.getLongArray(key).orElseGet(() -> new long[0]);
    *///? }
  }

  public static NbtList compounds(NbtCompound n, String key) {
    //? if <1.21.5 {
    return n.getList(key, NbtElement.COMPOUND_TYPE);
    //? } else {
    /*return n.getListOrEmpty(key);
    *///? }
  }

  public static NbtCompound compound(NbtList list, int index) {
    //? if <1.21.5 {
    return list.getCompound(index);
    //? } else {
    /*return list.getCompoundOrEmpty(index);
    *///? }
  }

  public static void writeItems(NbtCompound n,
      net.minecraft.util.collection.DefaultedList<net.minecraft.item.ItemStack> items,
      com.mojang.serialization.DynamicOps<NbtElement> ops) {
    NbtList list = new NbtList();
    for (int i = 0; i < items.size(); i++) {
      var stack = items.get(i);
      if (stack.isEmpty()) continue;
      int slot = i;
      net.minecraft.item.ItemStack.CODEC.encodeStart(ops, stack)
          .resultOrPartial(org.slf4j.LoggerFactory.getLogger("ReTerra")::error)
          .ifPresent(value -> {
            NbtCompound item = (NbtCompound) value;
            item.putByte("Slot", (byte) slot);
            list.add(item);
          });
    }
    n.put("Items", list);
  }

  public static void readItems(NbtCompound n,
      net.minecraft.util.collection.DefaultedList<net.minecraft.item.ItemStack> items,
      com.mojang.serialization.DynamicOps<NbtElement> ops) {
    for (NbtElement element : compounds(n, "Items")) {
      if (!(element instanceof NbtCompound item)) continue;
      int slot = integer(item, "Slot") & 255;
      if (slot >= items.size()) continue;
      net.minecraft.item.ItemStack.CODEC.parse(ops, item)
          .resultOrPartial(org.slf4j.LoggerFactory.getLogger("ReTerra")::error)
          .ifPresent(stack -> items.set(slot, stack));
    }
  }

  public static <S> com.mojang.serialization.Codec<S> stateCodec(
      java.util.function.BiFunction<NbtCompound, com.mojang.serialization.DynamicOps<NbtElement>, S> read,
      java.util.function.BiFunction<S, com.mojang.serialization.DynamicOps<NbtElement>, NbtCompound> write) {
    return new com.mojang.serialization.Codec<>() {
      private com.mojang.serialization.DynamicOps<NbtElement> nbtOps(
          com.mojang.serialization.DynamicOps<?> ops) {
        return ops instanceof net.minecraft.registry.RegistryOps<?> registryOps
            ? registryOps.withDelegate(net.minecraft.nbt.NbtOps.INSTANCE) : net.minecraft.nbt.NbtOps.INSTANCE;
      }

      @Override
      public <T> com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<S, T>> decode(
          com.mojang.serialization.DynamicOps<T> ops, T input) {
        return NbtCompound.CODEC.decode(ops, input)
            .map(pair -> com.mojang.datafixers.util.Pair.of(read.apply(pair.getFirst(), nbtOps(ops)), pair.getSecond()));
      }

      @Override
      public <T> com.mojang.serialization.DataResult<T> encode(S state,
          com.mojang.serialization.DynamicOps<T> ops, T prefix) {
        return NbtCompound.CODEC.encode(write.apply(state, nbtOps(ops)), ops, prefix);
      }
    };
  }

  public static void putUuid(NbtCompound n, String key, UUID id) {
    long most = id.getMostSignificantBits(), least = id.getLeastSignificantBits();
    n.putIntArray(key, new int[] {(int) (most >> 32), (int) most, (int) (least >> 32), (int) least});
  }

  public static boolean hasUuid(NbtCompound n, String key) {
    return ints(n, key).length == 4;
  }

  public static UUID uuid(NbtCompound n, String key) {
    int[] a = ints(n, key);
    if (a.length != 4) throw new IllegalArgumentException("Invalid UUID: " + key);
    return new UUID(((long) a[0] << 32) | (a[1] & 0xFFFFFFFFL),
        ((long) a[2] << 32) | (a[3] & 0xFFFFFFFFL));
  }
}
