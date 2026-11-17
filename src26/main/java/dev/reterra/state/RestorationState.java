package dev.reterra.state;

import dev.reterra.compat.NbtAccess;
import dev.reterra.ecology.Ecology;
import dev.reterra.machine.Balance;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

public final class RestorationState extends SavedData {
  private static final net.minecraft.world.level.saveddata.SavedDataType<RestorationState> TYPE =
      new net.minecraft.world.level.saveddata.SavedDataType<>(dev.reterra.Content.id("restoration"), RestorationState::new,
          NbtAccess.stateCodec(RestorationState::readData,
              (state, ops) -> state.writeData(new CompoundTag(), ops)), null);
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
  public transient net.minecraft.world.entity.Display.BlockDisplay
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

    CompoundTag write() {
      CompoundTag n = new CompoundTag();
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

    static Sector read(CompoundTag n) {
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

  public static final class Machine implements Container {
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
    public final NonNullList<ItemStack> items = NonNullList.withSize(8, ItemStack.EMPTY);
    public Runnable dirty = () -> {};
    public boolean production = true, reclaimed;
    public long lastWildlifeCheck;
    public boolean restorationNotified; // Session-only; resume notices never affect saved progression.
    public final Set<String> restoredSpecies = new HashSet<>();
    public String biome = "minecraft:plains";
    public Ecology.Family family = Ecology.Family.PLAINS;
    public final Sector[] sectors = new Sector[9];

    public Machine(BlockPos pos, String kind) {
      this.pos = pos.immutable();
      this.kind = kind;
      Arrays.setAll(sectors, i -> new Sector());
    }

    @Override
    public int getContainerSize() {
      return items.size();
    }

    @Override
    public boolean isEmpty() {
      return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
      return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
      var stack = ContainerHelper.removeItem(items, slot, amount);
      if (!stack.isEmpty()) setChanged();
      return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
      var stack = ContainerHelper.takeItem(items, slot);
      setChanged();
      return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
      items.set(slot, stack);
      stack.limitSize(getMaxStackSize());
      setChanged();
    }

    @Override
    public void setChanged() {
      dirty.run();
    }

    @Override
    public boolean stillValid(Player player) {
      return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    @Override
    public void clearContent() {
      items.clear();
      setChanged();
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

    CompoundTag write(com.mojang.serialization.DynamicOps<Tag> ops) {
      CompoundTag n = new CompoundTag();
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
      ListTag carers = new ListTag();
      for (UUID id : caretakers) {
        CompoundTag carer = new CompoundTag();
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
      ListTag list = new ListTag();
      for (Sector s : sectors) list.add(s.write());
      n.put("sectors", list);
      return n;
    }

    static Machine read(CompoundTag n, com.mojang.serialization.DynamicOps<Tag> ops) {
      Machine m = new Machine(BlockPos.of(NbtAccess.longValue(n, "pos")), NbtAccess.string(n, "kind"));
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
      for (Tag e : NbtAccess.compounds(n, "caretakers"))
        m.caretakers.add(NbtAccess.uuid(((CompoundTag) e), "id"));
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
      ListTag list = NbtAccess.compounds(n, "sectors");
      for (int i = 0; i < Math.min(9, list.size()); i++)
        m.sectors[i] = Sector.read(NbtAccess.compound(list, i));
      return m;
    }
  }

  public static RestorationState get(ServerLevel world) {
    var storage = world.getDataStorage();
    RestorationState existing = storage.get(TYPE);
    if (existing != null) return existing;
    RestorationState restored = importLegacy(world);
    storage.set(TYPE, restored);
    restored.setDirty();
    return restored;
  }
  private static RestorationState importLegacy(ServerLevel world) {
    // 26.1 namespaces saved-data files. Read an older copy once, leaving it intact.
    var root = world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
    var dimension = world.dimension().identifier();
    var directory = root.resolve("dimensions").resolve(dimension.getNamespace())
        .resolve(dimension.getPath()).resolve("data");
    var candidates = new java.util.ArrayList<java.nio.file.Path>();
    candidates.add(directory.resolve("reterra.dat"));
    candidates.add(directory.resolve("minecraft/reterra.dat"));
    if (world.dimension().equals(net.minecraft.world.level.Level.OVERWORLD))
      candidates.add(root.resolve("data/reterra.dat"));
    else if (world.dimension().equals(net.minecraft.world.level.Level.NETHER))
      candidates.add(root.resolve("DIM-1/data/reterra.dat"));
    else if (world.dimension().equals(net.minecraft.world.level.Level.END))
      candidates.add(root.resolve("DIM1/data/reterra.dat"));
    for (var file : candidates) {
      if (!java.nio.file.Files.isRegularFile(file)) continue;
      try {
        var saved = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        return fromNbt(saved.getCompoundOrEmpty("data"), world.registryAccess());
      } catch (java.io.IOException error) {
        throw new IllegalStateException("Cannot import ReTerra restoration data from " + file, error);
      }
    }
    return new RestorationState();
  }

  public CompoundTag writeNbt(CompoundTag n, HolderLookup.Provider registries) {
    return writeData(n, registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE));
  }

  private CompoundTag writeData(CompoundTag n, com.mojang.serialization.DynamicOps<Tag> ops) {
    n.putInt("version", 3);
    n.putString("recoveredSamples", String.join(",", recoveredSamples));
    ListTag list = new ListTag();
    machines.values().forEach(m -> list.add(m.write(ops)));
    n.put("machines", list);
    ListTag regions = new ListTag();
    reclaimedRegions.values().forEach(m -> regions.add(m.write(ops)));
    n.put("regions", regions);
    n.putLongArray("protected", protectedBlocks.stream().mapToLong(Long::longValue).toArray());
    ListTag players = new ListTag();
    for (UUID id : startedPlayers) {
      CompoundTag p = new CompoundTag();
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
    ListTag trees = new ListTag();
    treeJobs.values().forEach(job -> trees.add(job.write()));
    n.put("treeJobs", trees);
    ListTag notes = new ListTag();
    discoveries.forEach(
        (id, entries) -> {
          CompoundTag note = new CompoundTag();
          NbtAccess.putUuid(note, "player", id);
          note.putString("entries", String.join(",", entries));
          notes.add(note);
        });
    n.put("discoveries", notes);
    ListTag sites = new ListTag();
    knownSites.forEach(
        (pos, kind) -> {
          CompoundTag site = new CompoundTag();
          site.putLong("pos", pos);
          site.putString("kind", kind);
          sites.add(site);
        });
    n.put("sites", sites);
    return n;
  }

  public static RestorationState fromNbt(CompoundTag n, HolderLookup.Provider registries) {
    return readData(n, registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE));
  }

  private static RestorationState readData(CompoundTag n, com.mojang.serialization.DynamicOps<Tag> ops) {
    RestorationState s = new RestorationState();
    for (Tag e : NbtAccess.compounds(n, "machines")) {
      Machine m = Machine.read((CompoundTag) e, ops);
      m.dirty = s::setDirty;
      if (m.reclaimed) s.reclaimedRegions.put(m.pos.asLong(), m);
      else s.machines.put(m.pos.asLong(), m);
    }
    for (Tag e : NbtAccess.compounds(n, "regions")) {
      Machine m = Machine.read((CompoundTag) e, ops);
      m.dirty = s::setDirty;
      s.reclaimedRegions.put(m.pos.asLong(), m);
    }
    for (long p : NbtAccess.longs(n, "protected")) s.protectedBlocks.add(p);
    for (Tag e : NbtAccess.compounds(n, "players"))
      s.startedPlayers.add(NbtAccess.uuid(((CompoundTag) e), "id"));
    for (String f : NbtAccess.string(n, "completed").split(","))
      try {
        s.completed.add(Ecology.Family.valueOf(f));
      } catch (IllegalArgumentException ignored) {
      }
    s.heartActivated = NbtAccess.bool(n, "heart");
    if (!NbtAccess.string(n, "recoveredSamples").isEmpty())
      s.recoveredSamples.addAll(List.of(NbtAccess.string(n, "recoveredSamples").split(",")));
    if (n.contains("heartPos")) s.heartPos = BlockPos.of(NbtAccess.longValue(n, "heartPos"));
    if (n.contains("firstSite")) s.firstSite = BlockPos.of(NbtAccess.longValue(n, "firstSite"));
    s.firstSitePending = NbtAccess.bool(n, "firstSitePending");
    s.firstSiteCursor = NbtAccess.integer(n, "firstSiteCursor");
    s.heartTicks = NbtAccess.integer(n, "heartTicks");
    s.heartCursor = NbtAccess.integer(n, "heartCursor");
    s.heartAwakening =
        Math.clamp(NbtAccess.integer(n, "heartAwakening"), 0, dev.reterra.world.HeartAwakening.DURATION);
    if (NbtAccess.hasUuid(n, "heartCrystal")) s.heartCrystal = NbtAccess.uuid(n, "heartCrystal");
    for (Tag element : NbtAccess.compounds(n, "treeJobs")) {
      if (s.treeJobs.size() >= dev.reterra.world.TreeRecovery.MAX_JOBS) break;
      var job = dev.reterra.world.TreeRecovery.Job.read((CompoundTag) element);
      s.treeJobs.put(job.base.asLong(), job);
    }
    for (Tag e : NbtAccess.compounds(n, "discoveries")) {
      CompoundTag note = (CompoundTag) e;
      s.discoveries.put(
          NbtAccess.uuid(note, "player"), new HashSet<>(List.of(NbtAccess.string(note, "entries").split(","))));
    }
    for (Tag e : NbtAccess.compounds(n, "sites")) {
      var site = (CompoundTag) e;
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

  private static RestorationState read(CompoundTag n, HolderLookup.Provider registries) {
    return fromNbt(n, registries);
  }
}
