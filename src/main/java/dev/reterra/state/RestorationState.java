package dev.reterra.state;

import dev.reterra.compat.NbtAccess;
import dev.reterra.ecology.Ecology;
import dev.reterra.machine.Balance;
import java.util.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

public final class RestorationState extends PersistentState {
  //? if <1.21.5 {
  private static final Type<RestorationState> TYPE =
      new Type<>(RestorationState::new, RestorationState::read, null);
  //? } else if <1.21.11 {
  /*private static final net.minecraft.world.PersistentStateType<RestorationState> TYPE =
      new net.minecraft.world.PersistentStateType<>("reterra", context -> new RestorationState(),
          context -> NbtCompound.CODEC.xmap(
              n -> fromNbt(n, context.getWorldOrThrow().getRegistryManager()),
              state -> state.writeNbt(new NbtCompound(), context.getWorldOrThrow().getRegistryManager())), null);
  *///? } else {
  /*private static final net.minecraft.world.PersistentStateType<RestorationState> TYPE =
      new net.minecraft.world.PersistentStateType<>("reterra", RestorationState::new,
          NbtAccess.stateCodec(RestorationState::readData,
              (state, ops) -> state.writeData(new NbtCompound(), ops)), null);
  *///? }
  public final Map<Long, Machine> machines = new LinkedHashMap<>();
  public final Map<Long, Machine> reclaimedRegions = new LinkedHashMap<>();
  public final Set<Long> protectedBlocks = new HashSet<>();
  public final Set<UUID> startedPlayers = new HashSet<>();
  public final Set<Ecology.Family> completed = EnumSet.noneOf(Ecology.Family.class);
  public final Map<Long, String> knownSites = new LinkedHashMap<>();
  public boolean heartActivated;
  public BlockPos heartPos, firstSite;
  public boolean firstSitePending;
  public int firstSiteCursor, heartTicks, heartCursor;
  public final Map<UUID, Set<String>> discoveries = new HashMap<>();
  public final Set<String> recoveredSamples = new HashSet<>();

  public record PreviewBorder(BlockPos pos, long until) {}

  public final Map<UUID, PreviewBorder> previewBorders = new HashMap<>();
  public final Set<UUID> boundaryViewers = new HashSet<>();
  public int scheduler;
  public final Map<Long, dev.reterra.world.TreeRecovery.Job> treeJobs = new LinkedHashMap<>();
  public transient int treePlansRemaining = 4;
  public int heartAwakening;
  public UUID heartCrystal;
  public transient net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity
      heartCrystalHandle;
  public transient int heartCrystalWait;
  public final transient Map<
          dev.reterra.effect.AnimationSignals.Key, dev.reterra.effect.AnimationPayload>
      visualSignals = new LinkedHashMap<>();

  public static final class Sector {
    public int cursor, sampled, fertile, grass, water, crops, trees, flowers;
    public final Set<String> species = new HashSet<>();
    public int lastSampled, lastFertile, lastGrass, lastWater, lastCrops, lastTrees, lastFlowers;
    public final Set<String> lastSpecies = new HashSet<>();
    public boolean surveyed;
    public boolean biomeTransitioned;
    public int wetland, slopes, lastWetland, lastSlopes;
    public int restorable, lastRestorable;

    public void finish() {
      surveyed = true;
      lastSampled = sampled;
      lastFertile = fertile;
      lastGrass = grass;
      lastWater = water;
      lastCrops = crops;
      lastTrees = trees;
      lastFlowers = flowers;
      lastWetland = wetland;
      lastSlopes = slopes;
      lastRestorable = restorable;
      lastSpecies.clear();
      lastSpecies.addAll(species);
      cursor = sampled = fertile = grass = water = crops = trees = flowers = 0;
      wetland = slopes = 0;
      restorable = 0;
      species.clear();
    }

    NbtCompound write() {
      NbtCompound n = new NbtCompound();
      n.putBoolean("surveyed", surveyed);
      n.putBoolean("biomeTransitioned", biomeTransitioned);
      n.putInt("restorable", restorable);
      n.putInt("lastRestorable", lastRestorable);
      n.putIntArray("ecology", new int[] {wetland, slopes, lastWetland, lastSlopes});
      n.putIntArray(
          "values",
          new int[] {
            cursor,
            sampled,
            fertile,
            grass,
            water,
            crops,
            trees,
            flowers,
            lastSampled,
            lastFertile,
            lastGrass,
            lastWater,
            lastCrops,
            lastTrees,
            lastFlowers
          });
      n.putString("species", String.join(",", species));
      n.putString("lastSpecies", String.join(",", lastSpecies));
      return n;
    }

    static Sector read(NbtCompound n) {
      Sector s = new Sector();
      int[] a = NbtAccess.ints(n, "values");
      s.surveyed = NbtAccess.bool(n, "surveyed");
      s.biomeTransitioned = NbtAccess.bool(n, "biomeTransitioned");
      s.restorable = NbtAccess.integer(n, "restorable");
      s.lastRestorable = NbtAccess.integer(n, "lastRestorable");
      int[] ecology = NbtAccess.ints(n, "ecology");
      if (ecology.length == 4) {
        s.wetland = ecology[0];
        s.slopes = ecology[1];
        s.lastWetland = ecology[2];
        s.lastSlopes = ecology[3];
      }
      if (a.length == 15) {
        s.cursor = a[0];
        s.sampled = a[1];
        s.fertile = a[2];
        s.grass = a[3];
        s.water = a[4];
        s.crops = a[5];
        s.trees = a[6];
        s.flowers = a[7];
        s.lastSampled = a[8];
        s.lastFertile = a[9];
        s.lastGrass = a[10];
        s.lastWater = a[11];
        s.lastCrops = a[12];
        s.lastTrees = a[13];
        s.lastFlowers = a[14];
      }
      if (!NbtAccess.string(n, "species").isEmpty())
        s.species.addAll(List.of(NbtAccess.string(n, "species").split(",")));
      if (!NbtAccess.string(n, "lastSpecies").isEmpty())
        s.lastSpecies.addAll(List.of(NbtAccess.string(n, "lastSpecies").split(",")));
      return s;
    }
  }

  public static final class Machine implements Inventory {
    public final BlockPos pos;
    public final String kind;
    public int compost, fuel, water, cursor, sectorCursor;
    public int culture;
    public int stableSeconds, rewildCursor;
    public int revivalTicks, spreadRadius = 1, spreadTicks, biomeCursor;
    public int celebratedStage, riverCursor, riverRestored;
    public boolean riverMode;
    public boolean introductory;
    public final Set<UUID> caretakers = new HashSet<>();
    public final Set<String> careTasks = new HashSet<>();
    public String pendingSpecies = "";
    public final Set<Long> biomeChunks = new HashSet<>();
    public long lastStableTick;
    public boolean registered, paused;
    public int feedback;
    public final DefaultedList<ItemStack> items = DefaultedList.ofSize(8, ItemStack.EMPTY);
    public Runnable dirty = () -> {};
    public boolean production = true, reclaimed;
    public long lastWildlifeCheck;
    public final Set<String> restoredSpecies = new HashSet<>();
    public String biome = "minecraft:plains";
    public Ecology.Family family = Ecology.Family.PLAINS;
    public final Sector[] sectors = new Sector[9];

    public Machine(BlockPos pos, String kind) {
      this.pos = pos.toImmutable();
      this.kind = kind;
      Arrays.setAll(sectors, i -> new Sector());
    }

    @Override
    public int size() {
      return items.size();
    }

    @Override
    public boolean isEmpty() {
      return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getStack(int slot) {
      return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
      var stack = Inventories.splitStack(items, slot, amount);
      if (!stack.isEmpty()) markDirty();
      return stack;
    }

    @Override
    public ItemStack removeStack(int slot) {
      var stack = Inventories.removeStack(items, slot);
      markDirty();
      return stack;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
      items.set(slot, stack);
      stack.capCount(getMaxCountPerStack());
      markDirty();
    }

    @Override
    public void markDirty() {
      dirty.run();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
      return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    @Override
    public void clear() {
      items.clear();
      markDirty();
    }

    public Ecology.Survey survey(int animals) {
      int sampled = 0,
          fertile = 0,
          grass = 0,
          water = 0,
          crops = 0,
          trees = 0,
          flowers = 0,
          wetland = 0,
          slopes = 0;
      Set<String> species = new HashSet<>();
      for (Sector s : sectors) {
        sampled += s.lastSampled;
        fertile += s.lastFertile;
        grass += s.lastGrass;
        water += s.lastWater;
        crops += s.lastCrops;
        trees += s.lastTrees;
        flowers += s.lastFlowers;
        wetland += s.lastWetland;
        slopes += s.lastSlopes;
        species.addAll(s.lastSpecies);
      }
      return new Ecology.Survey(
          sampled,
          fertile,
          grass,
          water,
          crops,
          trees,
          flowers,
          species.size(),
          animals,
          (int) species.stream().filter(id -> id.endsWith("_leaves")).count(),
          wetland,
          slopes);
    }

    NbtCompound write(com.mojang.serialization.DynamicOps<NbtElement> ops) {
      NbtCompound n = new NbtCompound();
      n.putLong("pos", pos.asLong());
      n.putString("kind", kind);
      n.putInt("compost", compost);
      n.putInt("fuel", fuel);
      n.putInt("water", water);
      n.putInt("resourceVersion", 3);
      n.putInt("culture", culture);
      n.putInt("stableSeconds", stableSeconds);
      n.putInt("rewildCursor", rewildCursor);
      n.putInt("revivalTicks", revivalTicks);
      n.putInt("celebratedStage", celebratedStage);
      n.putBoolean("introductory", introductory);
      n.putString("careTasks", String.join(",", careTasks));
      NbtList carers = new NbtList();
      for (UUID id : caretakers) {
        NbtCompound carer = new NbtCompound();
        NbtAccess.putUuid(carer, "id", id);
        carers.add(carer);
      }
      n.put("caretakers", carers);
      n.putBoolean("riverMode", riverMode);
      n.putInt("riverCursor", riverCursor);
      n.putInt("riverRestored", riverRestored);
      n.putString("pendingSpecies", pendingSpecies);
      n.putInt("spreadRadius", spreadRadius);
      n.putInt("spreadTicks", spreadTicks);
      n.putInt("biomeCursor", biomeCursor);
      n.putLongArray("biomeChunks", biomeChunks.stream().mapToLong(Long::longValue).toArray());
      n.putBoolean("registered", registered);
      n.putBoolean("paused", paused);
      NbtAccess.writeItems(n, items, ops);
      n.putInt("cursor", cursor);
      n.putInt("sectorCursor", sectorCursor);
      n.putBoolean("production", production);
      n.putBoolean("reclaimed", reclaimed);
      n.putString("biome", biome);
      n.putString("family", family.name());
      n.putLong("lastWildlifeCheck", lastWildlifeCheck);
      n.putString("restoredSpecies", String.join(",", restoredSpecies));
      NbtList list = new NbtList();
      for (Sector s : sectors) list.add(s.write());
      n.put("sectors", list);
      return n;
    }

    static Machine read(NbtCompound n, com.mojang.serialization.DynamicOps<NbtElement> ops) {
      Machine m = new Machine(BlockPos.fromLong(NbtAccess.longValue(n, "pos")), NbtAccess.string(n, "kind"));
      m.compost = NbtAccess.integer(n, "compost");
      m.fuel = NbtAccess.integer(n, "fuel");
      m.water = NbtAccess.integer(n, "water");
      m.culture = NbtAccess.integer(n, "culture");
      m.stableSeconds = NbtAccess.integer(n, "stableSeconds");
      m.rewildCursor = NbtAccess.integer(n, "rewildCursor");
      m.revivalTicks = NbtAccess.integer(n, "revivalTicks");
      m.celebratedStage = NbtAccess.integer(n, "celebratedStage");
      m.introductory = NbtAccess.bool(n, "introductory");
      if (!NbtAccess.string(n, "careTasks").isEmpty())
        m.careTasks.addAll(List.of(NbtAccess.string(n, "careTasks").split(",")));
      for (NbtElement e : NbtAccess.compounds(n, "caretakers"))
        m.caretakers.add(NbtAccess.uuid(((NbtCompound) e), "id"));
      m.riverMode = NbtAccess.bool(n, "riverMode");
      m.riverCursor = NbtAccess.integer(n, "riverCursor");
      m.riverRestored = NbtAccess.integer(n, "riverRestored");
      m.pendingSpecies = NbtAccess.string(n, "pendingSpecies");
      m.spreadRadius = Math.clamp(NbtAccess.integer(n, "spreadRadius"), 1, Balance.MAX_SPREAD_RADIUS);
      m.spreadTicks = NbtAccess.integer(n, "spreadTicks");
      m.biomeCursor = NbtAccess.integer(n, "biomeCursor");
      for (long chunk : NbtAccess.longs(n, "biomeChunks")) m.biomeChunks.add(chunk);
      m.registered =
          n.contains("registered") ? NbtAccess.bool(n, "registered") : m.kind.equals("restoration_core");
      m.paused = NbtAccess.bool(n, "paused");
      NbtAccess.readItems(n, m.items, ops);
      if (NbtAccess.integer(n, "resourceVersion") < 2) {
        m.compost = Math.min(Balance.CAPACITY, m.compost * Balance.COMPOST_WORK);
        m.fuel = Math.min(Balance.CAPACITY, m.fuel * 256);
        m.water = Math.min(Balance.CAPACITY, m.water * 64);
        if (m.kind.equals("restoration_core")) m.culture = Balance.CULTURE_WORK;
      }
      if (NbtAccess.integer(n, "resourceVersion") < 3) {
        m.compost =
            (int)
                Math.ceil(
                    m.compost
                        * (m.kind.equals("water_restorer") ? 0.5 : Balance.COMPOST_WORK / 256.0));
        m.fuel = (int) Math.ceil(m.fuel * Balance.COAL_WORK / 2048.0);
        m.water =
            Math.min(Balance.TANK_CAPACITY, (int) Math.ceil(m.water * Balance.WATER_WORK / 2048.0));
        m.culture = (int) Math.ceil(m.culture * Balance.CULTURE_WORK / 1024.0);
      }
      m.cursor = NbtAccess.integer(n, "cursor");
      m.sectorCursor = NbtAccess.integer(n, "sectorCursor");
      m.production = NbtAccess.bool(n, "production");
      m.reclaimed = NbtAccess.bool(n, "reclaimed");
      m.biome = NbtAccess.string(n, "biome");
      m.lastWildlifeCheck = NbtAccess.longValue(n, "lastWildlifeCheck");
      if (!NbtAccess.string(n, "restoredSpecies").isEmpty())
        m.restoredSpecies.addAll(List.of(NbtAccess.string(n, "restoredSpecies").split(",")));
      try {
        m.family = Ecology.Family.valueOf(NbtAccess.string(n, "family"));
      } catch (IllegalArgumentException ignored) {
      }
      NbtList list = NbtAccess.compounds(n, "sectors");
      for (int i = 0; i < Math.min(9, list.size()); i++)
        m.sectors[i] = Sector.read(NbtAccess.compound(list, i));
      return m;
    }
  }

  public static RestorationState get(ServerWorld world) {
    //? if <1.21.5 {
    return world.getPersistentStateManager().getOrCreate(TYPE, "reterra");
    //? } else {
    /*return world.getPersistentStateManager().getOrCreate(TYPE);
    *///? }
  }

  //? if <1.21.5 {
  @Override
  //? }
  public NbtCompound writeNbt(NbtCompound n, RegistryWrapper.WrapperLookup registries) {
    return writeData(n, registries.getOps(net.minecraft.nbt.NbtOps.INSTANCE));
  }

  private NbtCompound writeData(NbtCompound n, com.mojang.serialization.DynamicOps<NbtElement> ops) {
    n.putInt("version", 3);
    n.putString("recoveredSamples", String.join(",", recoveredSamples));
    NbtList list = new NbtList();
    machines.values().forEach(m -> list.add(m.write(ops)));
    n.put("machines", list);
    NbtList regions = new NbtList();
    reclaimedRegions.values().forEach(m -> regions.add(m.write(ops)));
    n.put("regions", regions);
    n.putLongArray("protected", protectedBlocks.stream().mapToLong(Long::longValue).toArray());
    NbtList players = new NbtList();
    for (UUID id : startedPlayers) {
      NbtCompound p = new NbtCompound();
      NbtAccess.putUuid(p, "id", id);
      players.add(p);
    }
    n.put("players", players);
    n.putString("completed", String.join(",", completed.stream().map(Enum::name).toList()));
    n.putBoolean("heart", heartActivated);
    if (heartPos != null) n.putLong("heartPos", heartPos.asLong());
    if (firstSite != null) n.putLong("firstSite", firstSite.asLong());
    n.putBoolean("firstSitePending", firstSitePending);
    n.putInt("firstSiteCursor", firstSiteCursor);
    n.putInt("heartTicks", heartTicks);
    n.putInt("heartCursor", heartCursor);
    n.putInt("heartAwakening", heartAwakening);
    if (heartCrystal != null) NbtAccess.putUuid(n, "heartCrystal", heartCrystal);
    NbtList trees = new NbtList();
    treeJobs.values().forEach(job -> trees.add(job.write()));
    n.put("treeJobs", trees);
    NbtList notes = new NbtList();
    discoveries.forEach(
        (id, entries) -> {
          NbtCompound note = new NbtCompound();
          NbtAccess.putUuid(note, "player", id);
          note.putString("entries", String.join(",", entries));
          notes.add(note);
        });
    n.put("discoveries", notes);
    NbtList sites = new NbtList();
    knownSites.forEach(
        (pos, kind) -> {
          NbtCompound site = new NbtCompound();
          site.putLong("pos", pos);
          site.putString("kind", kind);
          sites.add(site);
        });
    n.put("sites", sites);
    return n;
  }

  public static RestorationState fromNbt(NbtCompound n, RegistryWrapper.WrapperLookup registries) {
    return readData(n, registries.getOps(net.minecraft.nbt.NbtOps.INSTANCE));
  }

  private static RestorationState readData(NbtCompound n, com.mojang.serialization.DynamicOps<NbtElement> ops) {
    RestorationState s = new RestorationState();
    for (NbtElement e : NbtAccess.compounds(n, "machines")) {
      Machine m = Machine.read((NbtCompound) e, ops);
      m.dirty = s::markDirty;
      if (m.reclaimed) s.reclaimedRegions.put(m.pos.asLong(), m);
      else s.machines.put(m.pos.asLong(), m);
    }
    for (NbtElement e : NbtAccess.compounds(n, "regions")) {
      Machine m = Machine.read((NbtCompound) e, ops);
      m.dirty = s::markDirty;
      s.reclaimedRegions.put(m.pos.asLong(), m);
    }
    for (long p : NbtAccess.longs(n, "protected")) s.protectedBlocks.add(p);
    for (NbtElement e : NbtAccess.compounds(n, "players"))
      s.startedPlayers.add(NbtAccess.uuid(((NbtCompound) e), "id"));
    for (String f : NbtAccess.string(n, "completed").split(","))
      try {
        s.completed.add(Ecology.Family.valueOf(f));
      } catch (IllegalArgumentException ignored) {
      }
    s.heartActivated = NbtAccess.bool(n, "heart");
    if (!NbtAccess.string(n, "recoveredSamples").isEmpty())
      s.recoveredSamples.addAll(List.of(NbtAccess.string(n, "recoveredSamples").split(",")));
    if (n.contains("heartPos")) s.heartPos = BlockPos.fromLong(NbtAccess.longValue(n, "heartPos"));
    if (n.contains("firstSite")) s.firstSite = BlockPos.fromLong(NbtAccess.longValue(n, "firstSite"));
    s.firstSitePending = NbtAccess.bool(n, "firstSitePending");
    s.firstSiteCursor = NbtAccess.integer(n, "firstSiteCursor");
    s.heartTicks = NbtAccess.integer(n, "heartTicks");
    s.heartCursor = NbtAccess.integer(n, "heartCursor");
    s.heartAwakening =
        Math.clamp(NbtAccess.integer(n, "heartAwakening"), 0, dev.reterra.world.HeartAwakening.DURATION);
    if (NbtAccess.hasUuid(n, "heartCrystal")) s.heartCrystal = NbtAccess.uuid(n, "heartCrystal");
    for (NbtElement element : NbtAccess.compounds(n, "treeJobs")) {
      if (s.treeJobs.size() >= dev.reterra.world.TreeRecovery.MAX_JOBS) break;
      var job = dev.reterra.world.TreeRecovery.Job.read((NbtCompound) element);
      s.treeJobs.put(job.base.asLong(), job);
    }
    for (NbtElement e : NbtAccess.compounds(n, "discoveries")) {
      NbtCompound note = (NbtCompound) e;
      s.discoveries.put(
          NbtAccess.uuid(note, "player"), new HashSet<>(List.of(NbtAccess.string(note, "entries").split(","))));
    }
    for (NbtElement e : NbtAccess.compounds(n, "sites")) {
      var site = (NbtCompound) e;
      s.knownSites.put(NbtAccess.longValue(site, "pos"), NbtAccess.string(site, "kind"));
    }
    if (NbtAccess.integer(n, "version") < 3 && s.completed.isEmpty())
      s.machines.values().stream()
          .filter(m -> m.registered && m.kind.equals("restoration_core"))
          .findFirst()
          .ifPresent(m -> m.introductory = true);
    for (Machine m :
        java.util.stream.Stream.concat(
                s.machines.values().stream(), s.reclaimedRegions.values().stream())
            .toList())
      for (String species : m.restoredSpecies) {
        String name = species.substring(species.indexOf(':') + 1);
        if (Set.of("sheep", "cow", "chicken", "bee").contains(name))
          s.recoveredSamples.add(name + "_sample");
      }
    return s;
  }

  private static RestorationState read(NbtCompound n, RegistryWrapper.WrapperLookup registries) {
    return fromNbt(n, registries);
  }
}
