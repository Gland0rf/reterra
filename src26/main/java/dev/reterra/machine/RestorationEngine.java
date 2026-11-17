package dev.reterra.machine;

import dev.reterra.Content;
import dev.reterra.ecology.Ecology;
import dev.reterra.effect.AnimationPayload.Kind;
import dev.reterra.effect.AnimationSignals;
import dev.reterra.state.RestorationState;
import dev.reterra.state.RestorationState.Machine;
import dev.reterra.state.RestorationState.Sector;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class RestorationEngine {
  private RestorationEngine() {}

  // Every five ticks: at most 16 loaded records, with bounded scans and terrain writes.
  public static void tick(ServerLevel world) {
    if (!world.dimension().equals(Level.OVERWORLD) || world.getGameTime() % 5 != 0) return;
    RestorationState state = RestorationState.get(world);
    state.treePlansRemaining = 4;
    if (world.getGameTime() % 20 == 0) {
      dev.reterra.world.ResearchSites.tickFirstSite(world);
      dev.reterra.Journal.tick(world);
      dev.reterra.world.WorldRecovery.tick(world, state);
    }
    dev.reterra.world.TreeRecovery.tick(world, state);
    dev.reterra.world.HeartAwakening.tick(world, state);
    if (state.machines.isEmpty() && state.reclaimedRegions.isEmpty()) return;
    List<Machine> machines = new ArrayList<>(state.machines.values());
    machines.addAll(state.reclaimedRegions.values());
    machines.removeIf(m -> !loaded(world, m.pos));
    if (machines.isEmpty()) return;
    int count = Math.min(16, machines.size());
    for (int i = 0; i < count; i++) {
      Machine m = machines.get(Math.floorMod(state.scheduler++, machines.size()));
      if (!loaded(world, m.pos)) continue;
      MachineBlock.updateVisual(world, m);
      if (m.kind.equals("restoration_core") && world.getBlockState(m.pos).is(Content.CORE))
        MachineLogic.refundCultures(world, m);
      if (world.getGameTime() % 20 == 0) AnimalHabitatGoal.adoptNearby(world, m);
      if (world.getGameTime() % 20 == 0 && m.kind.equals("restoration_core"))
        dev.reterra.world.WorldRecovery.ambience(world, m);
      if (m.reclaimed) {
        advanceSpread(m, state.heartActivated ? 10 : 5);
        if (world.getGameTime() % 20 == 0) {
          dev.reterra.world.ReclaimedBiomes.tick(world, m);
          refreshSector(world, m);
        }
        rewild(world, state, m);
        // Recovery survives removal of the machine. Population maintenance needs no fuel.
        if (world.getGameTime() - m.lastWildlifeCheck >= 6000) {
          m.lastWildlifeCheck = world.getGameTime();
          if (animalCount(world, m) < 8 && !m.restoredSpecies.isEmpty()) {
            List<String> species = new ArrayList<>(m.restoredSpecies);
            String id = species.get(world.getRandom().nextInt(species.size()));
            spawnPair(world, m, revivedType(id));
          }
        }
        continue;
      }
      Block block = world.getBlockState(m.pos).getBlock();
      if (!Content.isMachine(block)) {
        state.machines.remove(m.pos.asLong());
        state.setDirty();
        continue;
      }
      boolean operating = !m.paused && Multiblocks.issue(world, m.pos, m.kind) == 0;
      if (block == Content.CORE && m.registered) {
        if (operating) MachineLogic.stock(world, m);
        core(world, state, m, operating);
        continue;
      }
      if (!operating) continue;
      MachineLogic.stock(world, m);
      if (block == Content.RECLAIMER) reclaimer(world, state, m);
      else if (block == Content.WATERWORKS && world.getGameTime() % 20 == 0)
        MachineLogic.pump(world, m);
      else if (block == Content.CHAMBER) MachineLogic.revivalTick(world, m);
      MachineBlock.updateVisual(world, m);
    }
    state.setDirty();
  }

  public static boolean loaded(ServerLevel w, BlockPos p) {
    return w.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4);
  }

  public static void advanceSpread(Machine m, int loadedTicks) {
    m.spreadTicks += loadedTicks;
    while (m.spreadTicks >= 1200 && m.spreadRadius < Balance.MAX_SPREAD_RADIUS) {
      m.spreadRadius++;
      m.spreadTicks -= 1200;
    }
    if (m.spreadRadius == Balance.MAX_SPREAD_RADIUS) m.spreadTicks = 0;
  }

  public static AABB bounds(Machine m) {
    int x = (m.pos.getX() >> 4) * 16, z = (m.pos.getZ() >> 4) * 16;
    return new AABB(x - 16, -64, z - 16, x + 32, 320, z + 32);
  }

  public static int animalCount(ServerLevel w, Machine m) {
    return w.getEntitiesOfClass(Animal.class, bounds(m).inflate(12), a -> resident(a, m))
            .size()
        + shelteredBees(w, m);
  }

  public static int animalSpecies(ServerLevel w, Machine m) {
    Set<EntityType<?>> types = new HashSet<>();
    w.getEntitiesOfClass(Animal.class, bounds(m).inflate(12), a -> resident(a, m))
        .forEach(a -> types.add(a.getType()));
    if (shelteredBees(w, m) > 0) types.add(dev.reterra.compat.GameAccess.BEE);
    return types.size();
  }

  private static int shelteredBees(ServerLevel world, Machine m) {
    int count = 0;
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++) {
        int cx = (m.pos.getX() >> 4) + dx, cz = (m.pos.getZ() >> 4) + dz;
        if (!world.getChunkSource().hasChunk(cx, cz)) continue;
        for (var block : world.getChunk(cx, cz).getBlockEntities().values())
          if (block instanceof BeehiveBlockEntity hive) count += hive.getOccupantCount();
      }
    return count;
  }

  private static boolean resident(Animal animal, Machine m) {
    return animal.isAlive()
        && !animal.isBaby()
        && (bounds(m).contains(dev.reterra.compat.WorldAccess.position(animal))
            || animal.entityTags().contains(AnimalHabitatGoal.PREFIX + m.pos.asLong()));
  }

  public static Machine regionAt(RestorationState state, BlockPos p) {
    List<Machine> regions = new ArrayList<>(state.reclaimedRegions.values());
    regions.addAll(state.machines.values());
    for (Machine m : regions)
      if (m.kind.equals("restoration_core")
          && (m.registered || m.reclaimed)
          && Ecology.inRegion(m.pos.getX() >> 4, m.pos.getZ() >> 4, p.getX(), p.getZ())) return m;
    return null;
  }

  private static void reclaimer(ServerLevel w, RestorationState s, Machine m) {
    if (m.compost < 1 || m.fuel < 1 || m.water < 1) return;
    if (m.production) {
      if (!(w.getBlockEntity(Multiblocks.barrel(w, m.pos)) instanceof Container inv)) return;
      int input = -1, output = -1;
      for (int i = 0; i < inv.getContainerSize(); i++) {
        ItemStack stack = inv.getItem(i);
        if (MachineLogic.feedstock(stack) && input < 0) input = i;
        if (output < 0
            && (stack.isEmpty()
                || (stack.is(Items.DIRT) && stack.getCount() < stack.getMaxStackSize()))) output = i;
      }
      boolean menuInput = MachineLogic.feedstock(m.getItem(3));
      boolean menuOutput = MachineLogic.room(m, 6, Items.DIRT, 1);
      if ((!menuInput && input < 0) || (!menuOutput && output < 0)) return;
      if (menuInput) m.removeItem(3, 1);
      else inv.removeItem(input, 1);
      if (menuOutput) MachineLogic.output(m, 6, Items.DIRT, 1);
      else if (inv.getItem(output).isEmpty()) inv.setItem(output, new ItemStack(Items.DIRT, 1));
      else inv.getItem(output).grow(1);
      inv.setChanged();
      m.compost--;
      m.fuel--;
      m.water--;
      AnimationSignals.emit(w, m.pos, m.pos, Kind.RECLAIMER, 0);
    } else {
      int changed = 0;
      for (int i = 0; i < 16 && changed < 8; i++) {
        int index = Math.floorMod(m.cursor++, 225);
        int x = m.pos.getX() + index % 15 - 7, z = m.pos.getZ() + index / 15 - 7;
        BlockPos p = ground(w, x, z);
        if (p == null) continue;
        if (terraform(w, s, m, p, false)) changed++;
      }
    }
  }

  private static void core(ServerLevel w, RestorationState state, Machine m, boolean operating) {
    if (!operating) m.restorationNotified = false;
    int changed = operating ? restoreTerrain(w, state, m) : 0;
    if (changed > 0 && !m.restorationNotified) {
      for (var player : w.players())
        if (bounds(m).contains(dev.reterra.compat.WorldAccess.position(player))) {
          player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.reterra.restoration_started"));
          m.restorationNotified = true;
        }
    }
    int sector = Math.floorMod(m.sectorCursor++, 9);
    int cx = (m.pos.getX() >> 4) + sector % 3 - 1, cz = (m.pos.getZ() >> 4) + sector / 3 - 1;
    if (!w.getChunkSource().hasChunk(cx, cz)) return;
    Sector s = m.sectors[sector];
    for (int i = 0; i < 32; i++) {
      int index = s.cursor++;
      int x = cx * 16 + index % 16, z = cz * 16 + index / 16;
      BlockPos p = ground(w, x, z);
      if (p != null) {
        sample(w, s, p);
      }
      if (s.cursor >= 256) {
        s.finish();
        break;
      }
    }
    int animalTypes = animalSpecies(w, m);
    Ecology.Stage stage = m.survey(animalTypes).stage(m.family, m.introductory);
    dev.reterra.Journal.regional(state, m, animalTypes);
    boolean allSurveyed = Arrays.stream(m.sectors).allMatch(v -> v.surveyed);
    if (allSurveyed) celebrate(w, state, m, stage == Ecology.Stage.RECLAIMED ? 3 : stage.ordinal());
    boolean observed = operating && allSurveyed && allSectorsLoaded(w, m);
    boolean healthy = stage == Ecology.Stage.RECLAIMED && animalTypes >= 2;
    // Unloading/pausing preserves progress. Observed habitat damage slowly reduces it.
    int expectedInterval =
        20 * Math.max(2, (state.machines.size() + state.reclaimedRegions.size() + 15) / 16 + 1);
    if (observed) updateStability(m, healthy, w.getGameTime(), expectedInterval);
    else m.lastStableTick = 0;
    if (m.stableSeconds >= Ecology.stabilityTarget(m.introductory)) {
      m.reclaimed = true;
      state.machines.remove(m.pos.asLong());
      state.reclaimedRegions.put(m.pos.asLong(), m);
      state.completed.add(m.family);
      dev.reterra.Journal.regional(state, m, animalTypes);
      celebrate(w, state, m, 4);
      for (var player : w.players())
        if (bounds(m).contains(dev.reterra.compat.WorldAccess.position(player))) dev.reterra.Journal.grant(player, "reclaimed");
    }
  }

  public static void updateStability(Machine m, boolean healthy, long tick) {
    updateStability(m, healthy, tick, 40);
  }

  public static int restoreTerrain(ServerLevel world, RestorationState state, Machine m) {
    int changed = 0;
    int budget = EcosystemBenefits.coreWrites(EcosystemBenefits.mask(state));
    // Resume after the last attempted column, independent of the survey's larger batches.
    for (int i = 0; i < 64 && changed < budget; i++) {
      BlockPos column = dev.reterra.world.RecoveryPattern.column(m.pos, 1, m.cursor++);
      BlockPos p = ground(world, column.getX(), column.getZ());
      if (p != null && terraform(world, state, m, p, false)) changed++;
    }
    return changed;
  }

  private static void updateStability(
      Machine m, boolean healthy, long tick, int intervalTolerance) {
    if (m.lastStableTick > 0) {
      long elapsed = tick - m.lastStableTick;
      if (elapsed > (healthy ? intervalTolerance : Math.max(120, intervalTolerance)) || elapsed < 0)
        m.lastStableTick = 0;
      else if (healthy && elapsed >= 20)
        m.stableSeconds =
            Math.min(
                Ecology.stabilityTarget(m.introductory), m.stableSeconds + (int) (elapsed / 20));
      else if (!healthy && elapsed >= 100) {
        m.stableSeconds = Math.max(0, m.stableSeconds - (int) (elapsed / 100));
        m.lastStableTick = tick;
      }
    }
    if (m.lastStableTick == 0 || healthy && tick - m.lastStableTick >= 20) m.lastStableTick = tick;
  }

  public static void celebrate(ServerLevel w, RestorationState state, Machine m, int stage) {
    if (stage <= m.celebratedStage) return;
    m.celebratedStage = stage;
    AnimationSignals.emit(w, m.pos, m.pos, Kind.MILESTONE, stage);
    String[] names = {
      "Barren", "Living soil", "First growth", "Wildlife habitat", "Ecosystem reclaimed"
    };
    for (var player : w.players())
      if (bounds(m).inflate(16).contains(dev.reterra.compat.WorldAccess.position(player))) {
        player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(names[Math.clamp(stage, 0, 4)]));
        dev.reterra.Journal.discover(player, "stage_" + stage);
      }
    w.playSound(
        null,
        m.pos,
        stage == 4
            ? net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE
            : net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
        net.minecraft.sounds.SoundSource.BLOCKS,
        .7f,
        .8f + stage * .15f);
    w.sendParticles(
        net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
        m.pos.getX() + .5,
        m.pos.getY() + 1,
        m.pos.getZ() + .5,
        30,
        3,
        1,
        3,
        .03);
    // A small, permanent plant patch makes milestones visible from the landscape too.
    if (stage >= 2)
      for (int i = 0, grown = 0; i < 96 && grown < 8; i++) {
        BlockPos p =
            ground(
                w,
                m.pos.getX() + w.getRandom().nextInt(25) - 12,
                m.pos.getZ() + w.getRandom().nextInt(25) - 12);
        if (p != null
            && bounds(m).contains(net.minecraft.world.phys.Vec3.atCenterOf(p))
            && w.getBlockState(p).is(Blocks.GRASS_BLOCK)
            && w.getBlockState(p.above()).isAir()
            && !state.protectedBlocks.contains(p.above().asLong())) {
          w.setBlockAndUpdate(
              p.above(), (grown++ % 2 == 0 ? Blocks.SHORT_GRASS : Blocks.DANDELION).defaultBlockState());
        }
      }
    state.setDirty();
  }

  public static boolean allSectorsLoaded(ServerLevel w, Machine m) {
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++)
        if (!w.getChunkSource().hasChunk((m.pos.getX() >> 4) + dx, (m.pos.getZ() >> 4) + dz))
          return false;
    return true;
  }

  private static void refreshSector(ServerLevel world, Machine m) {
    int index = Math.floorMod(m.sectorCursor++, 9);
    surveySector(world, m, index);
  }

  public static void surveySector(ServerLevel world, Machine m, int index) {
    int cx = (m.pos.getX() >> 4) + index % 3 - 1, cz = (m.pos.getZ() >> 4) + index / 3 - 1;
    if (!world.getChunkSource().hasChunk(cx, cz)) return;
    Sector sector = m.sectors[index];
    sector.cursor =
        sector.sampled =
            sector.fertile =
                sector.grass = sector.water = sector.crops = sector.trees = sector.flowers = 0;
    sector.wetland = sector.slopes = 0;
    sector.restorable = 0;
    sector.species.clear();
    for (int i = 0; i < 256; i++) {
      BlockPos p = ground(world, cx * 16 + i % 16, cz * 16 + i / 16);
      if (p != null) sample(world, sector, p);
    }
    sector.finish();
  }

  /** Passive succession after completion; no chunk loads, fuel or damage to player construction. */
  public static void rewild(ServerLevel w, RestorationState state, Machine m) {
    int radius =
        Math.clamp(
            m.spreadRadius, 1, m.kind.equals("heart_of_the_world") ? 8 : Balance.MAX_SPREAD_RADIUS);
    int writes = 0;
    for (int i = 0; i < 128 && writes < 8; i++) {
      BlockPos column = dev.reterra.world.RecoveryPattern.column(m.pos, radius, m.rewildCursor++);
      BlockPos surface = ground(w, column.getX(), column.getZ());
      BlockPos p = surface == null ? null : soilBelowSurface(w, surface);
      if (p == null || state.protectedBlocks.contains(p.asLong())) continue;
      Machine owner = regionAt(state, p);
      if (owner != null && (m.kind.equals("heart_of_the_world") || !owner.pos.equals(m.pos)))
        continue;
      String biome =
          m.kind.equals("heart_of_the_world")
              ? w.getBiome(surface).unwrapKey().orElseThrow().identifier().toString()
              : m.biome;
      Ecology.Family family =
          m.kind.equals("heart_of_the_world")
              ? dev.reterra.world.BarrenWorld.family(biome)
              : m.family;
      int variant = dev.reterra.ecology.HabitatPlants.variant(biome);
      boolean water = w.getFluidState(surface).is(net.minecraft.tags.FluidTags.WATER);
      BlockState soil = w.getBlockState(p);
      if (soil.is(Blocks.COARSE_DIRT) || soil.is(Blocks.GRAVEL)) {
        Block restored =
            water && family == Ecology.Family.SWAMP
                ? Blocks.MUD
                : !water && canGrowGrass(w, p) ? Blocks.GRASS_BLOCK : Blocks.DIRT;
        w.setBlock(p, restored.defaultBlockState(), Block.UPDATE_ALL);
        writes++;
      } else if (soil.is(Blocks.DIRT) && !water && canGrowGrass(w, p)) {
        w.setBlock(p, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        writes++;
      }
      var restoredSoil = w.getBlockState(p);
      if (restoredSoil.is(Blocks.DIRT)
          || restoredSoil.is(Blocks.GRASS_BLOCK)
          || restoredSoil.is(Blocks.MUD)
          || restoredSoil.is(Blocks.PODZOL)
          || restoredSoil.is(Blocks.MOSS_BLOCK))
        dev.reterra.world.TreeRecovery.enqueueColumn(w, state, m, p.above());
      BlockPos plantPos = water ? surface.above() : p.above();
      if (writes >= 8
          || !w.getBlockState(plantPos).isAir()
          || state.protectedBlocks.contains(plantPos.asLong())) continue;
      int detail = Ecology.scarHash(p.getX(), p.getZ());
      Block plant = null;
      if (water && family == Ecology.Family.SWAMP && detail % 37 == 0) plant = Blocks.LILY_PAD;
      else if (!water
          && w.getBlockState(p).is(Blocks.GRASS_BLOCK)
          && Ecology.scarHash(Math.floorDiv(p.getX(), 6), Math.floorDiv(p.getZ(), 6)) % 3 != 0
          && detail % 4 == 0) {
        var flowers = dev.reterra.ecology.HabitatPlants.flowers(family, variant);
        if (detail % 83 == 0 && w.canSeeSky(plantPos)) {
          var trees = dev.reterra.ecology.HabitatPlants.trees(family, variant);
          plant = trees.get(detail % trees.size());
        } else if (family == Ecology.Family.SWAMP && detail % 7 == 0 && waterside(w, p))
          plant = Blocks.SUGAR_CANE;
        else if (detail % 5 == 0) plant = flowers.get(detail % flowers.size());
        else
          plant =
              family == Ecology.Family.FOREST && variant != 1 ? Blocks.FERN : Blocks.SHORT_GRASS;
      }
      if (plant == Blocks.SUGAR_CANE) {
        boolean neighborsLoaded = true;
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL)
          neighborsLoaded &= loaded(w, p.relative(direction));
        if (!neighborsLoaded) continue;
      }
      if (plant != null && plant.defaultBlockState().canSurvive(w, plantPos)) {
        w.setBlock(plantPos, plant.defaultBlockState(), Block.UPDATE_ALL);
        writes++;
      }
    }
  }

  private static boolean canGrowGrass(ServerLevel world, BlockPos pos) {
    return world.getMaxLocalRawBrightness(pos.above()) >= 9
        && world.getFluidState(pos.above()).isEmpty()
        && !dev.reterra.compat.WorldAccess.opaque(world, pos.above());
  }

  public static boolean sameBiome(ServerLevel w, Machine m, BlockPos p) {
    return w.getBiome(p)
        .unwrapKey()
        .map(
            k ->
                k.identifier().toString().equals(m.biome)
                    || (m.reclaimed
                        && k.identifier().toString().equals(dev.reterra.world.ReclaimedBiomes.id(m))))
        .orElse(false);
  }

  /** Find terrain beneath foliage, branches and their air gaps; roofs still stop the scan. */
  public static BlockPos ground(ServerLevel w, int x, int z) {
    if (!w.getChunkSource().hasChunk(x >> 4, z >> 4)) return null;
    int y = w.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    for (int d = 0; d < 64 && y - d >= w.getMinY(); d++) {
      BlockPos p = new BlockPos(x, y - d, z);
      BlockState b = w.getBlockState(p);
      if (b.isAir()
          || b.is(BlockTags.LOGS)
          || b.is(BlockTags.LEAVES)
          || b.getBlock() instanceof VegetationBlock
          || b.is(Blocks.MANGROVE_ROOTS)
          || b.is(Blocks.MUDDY_MANGROVE_ROOTS)
          || b.is(Blocks.VINE)) continue;
      return p;
    }
    return null;
  }

  public static BlockPos soilBelowSurface(ServerLevel world, BlockPos surface) {
    BlockPos p = surface;
    for (int depth = 0; depth < 16 && p.getY() >= world.getMinY(); depth++, p = p.below()) {
      BlockState block = world.getBlockState(p);
      if (block.is(Blocks.WATER)
          || block.getBlock() instanceof VegetationBlock
          || block.is(Blocks.MANGROVE_ROOTS)
          || block.is(Blocks.MUDDY_MANGROVE_ROOTS)) continue;
      return block.isAir() ? null : p;
    }
    return null;
  }

  private static boolean terraform(
      ServerLevel w, RestorationState s, Machine m, BlockPos p, boolean grass) {
    p = soilBelowSurface(w, p);
    if (p == null) return false;
    if (s.protectedBlocks.contains(p.asLong()) || Ecology.isScar(p.getX(), p.getZ())) return false;
    if (m.fuel < 1 || m.water < 1) return false;
    BlockState old = w.getBlockState(p);
    BlockState target = null;
    boolean soil = old.is(Blocks.COARSE_DIRT) || old.is(Blocks.GRAVEL);
    if (m.kind.equals("restoration_core") && m.registered) {
      var goals = Ecology.goals(m.family, m.introductory);
      var survey = m.survey(0);
      if (soil && survey.soilPercent() >= goals.soil()) return false;
    }
    if (soil && m.compost > 0) target = Blocks.DIRT.defaultBlockState();
    if (target == null || w.getBlockEntity(p) != null) return false;
    w.setBlock(p, target, Block.UPDATE_ALL);
    if (soil) m.compost--;
    else m.culture--;
    m.fuel--;
    m.water--;
    AnimationSignals.emit(
        w, m.pos, p, m.kind.equals("restoration_core") ? Kind.CORE : Kind.RECLAIMER, 0);
    return true;
  }

  public static void sample(ServerLevel w, Sector s, BlockPos p) {
    s.sampled++;
    BlockPos bed = soilBelowSurface(w, p);
    BlockState ground = bed == null ? w.getBlockState(p) : w.getBlockState(bed);
    if ((ground.is(Blocks.COARSE_DIRT) || ground.is(Blocks.GRAVEL))
        && !RestorationState.get(w).protectedBlocks.contains((bed == null ? p : bed).asLong())
        && !Ecology.isScar(p.getX(), p.getZ())) s.restorable++;
    if (ground.is(Blocks.DIRT)
        || ground.is(Blocks.GRASS_BLOCK)
        || ground.is(Blocks.FARMLAND)
        || ground.is(Blocks.MUD)
        || ground.is(Blocks.PODZOL)
        || ground.is(Blocks.MOSS_BLOCK)
        || ground.is(Blocks.MYCELIUM)) {
      s.fertile++;
      for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
        BlockPos adjacent = p.relative(direction);
        if (!loaded(w, adjacent)
            || dev.reterra.compat.WorldAccess.opaque(w, adjacent)
            || !w.getFluidState(adjacent).isEmpty()) continue;
        boolean slope = false;
        for (int drop = 1; drop <= 4; drop++) {
          BlockPos lower = adjacent.below(drop);
          if (dev.reterra.compat.WorldAccess.opaque(w, lower)) {
            slope = true;
            break;
          }
        }
        if (slope) {
          s.slopes++;
          break;
        }
      }
    }
    if (w.getBlockState(p).is(Blocks.GRASS_BLOCK)) s.grass++;
    if (w.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) s.water++;
    boolean tree = false, flower = false, crop = false;
    // Count actual plants/leaves and water next to surface, not donated items.
    for (int y = 1; y <= 32 && p.getY() + y < (w.getMinY() + w.getHeight()); y++) {
      BlockState plant = w.getBlockState(p.above(y));
      if (plant.is(BlockTags.LEAVES)) {
        tree = true;
        s.species.add(BuiltInRegistries.BLOCK.getKey(plant.getBlock()).toString());
      }
      if (y <= 2) {
        if (y == 1 && plant.is(BlockTags.LOGS) && waterside(w, p)) s.wetland++;
        if (plant.is(Blocks.SUGAR_CANE)
            || plant.is(Blocks.LILY_PAD)
            || plant.is(Blocks.VINE)
            || plant.is(Blocks.BROWN_MUSHROOM)
            || plant.is(Blocks.RED_MUSHROOM)
            || dev.reterra.compat.GameAccess.sapling(plant)) {
          s.species.add(BuiltInRegistries.BLOCK.getKey(plant.getBlock()).toString());
          if (y == 1 && waterside(w, p)) s.wetland++;
        }
        if (plant.is(BlockTags.FLOWERS)) {
          flower = true;
          s.species.add(BuiltInRegistries.BLOCK.getKey(plant.getBlock()).toString());
        }
        if (plant.getBlock() instanceof CropBlock) {
          crop = true;
          s.species.add(BuiltInRegistries.BLOCK.getKey(plant.getBlock()).toString());
        }
      }
    }
    if (tree) s.trees++;
    if (flower) s.flowers++;
    if (crop) s.crops++;
  }

  private static boolean waterside(ServerLevel w, BlockPos p) {
    if (w.getBlockState(p).is(Blocks.WATER)) return true;
    for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
      BlockPos side = p.relative(d);
      if (loaded(w, side)
          && (w.getBlockState(side).is(Blocks.WATER)
              || w.getBlockState(side.below()).is(Blocks.WATER))) return true;
    }
    return false;
  }

  public static Ecology.Survey localSurvey(ServerLevel world, BlockPos center) {
    Sector sector = new Sector();
    for (int dx = -8; dx < 8; dx++)
      for (int dz = -8; dz < 8; dz++) {
        BlockPos p = ground(world, center.getX() + dx, center.getZ() + dz);
        if (p != null) sample(world, sector, p);
      }
    int types =
        (int)
            world
                .getEntitiesOfClass(
                    Animal.class,
                    new AABB(center).inflate(8, 64, 8),
                    a -> a.isAlive() && !a.isBaby())
                .stream()
                .map(Animal::getType)
                .distinct()
                .count();
    return new Ecology.Survey(
        sector.sampled,
        sector.fertile,
        sector.grass,
        sector.water,
        sector.crops,
        sector.trees,
        sector.flowers,
        sector.species.size(),
        types,
        (int) sector.species.stream().filter(id -> id.endsWith("_leaves")).count(),
        sector.wetland,
        sector.slopes);
  }

  public static int spawnPair(ServerLevel w, Machine m, EntityType<? extends Animal> type) {
    return spawnPair(w, m, type, m.pos);
  }

  public static int spawnPair(
      ServerLevel w, Machine m, EntityType<? extends Animal> type, BlockPos origin) {
    List<Animal> pair = new ArrayList<>();
    for (int attempt = 0; attempt < 48 && pair.size() < 2; attempt++) {
      int x = origin.getX() + w.getRandom().nextInt(13) - 6,
          z = origin.getZ() + w.getRandom().nextInt(13) - 6;
      BlockPos ground = ground(w, x, z);
      if (ground == null || !bounds(m).contains(net.minecraft.world.phys.Vec3.atCenterOf(ground)))
        continue;
      if (!w.getBlockState(ground).is(Blocks.GRASS_BLOCK)
          || !w.getBlockState(ground.above()).isAir()
          || !w.getBlockState(ground.above(2)).isAir()) continue;
      Animal animal = type.create(w, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
      if (animal == null) continue;
      animal.snapTo(
          x + 0.5, ground.getY() + 1, z + 0.5, w.getRandom().nextFloat() * 360, 0);
      animal.addTag("reterra_revived");
      animal.addTag(AnimalHabitatGoal.PREFIX + m.pos.asLong());
      animal.setHomeTo(m.pos, 20);
      animal.setPersistenceRequired();
      if (!w.noCollision(animal)
          || pair.stream().anyMatch(other -> other.distanceToSqr(animal) < 2)) {
        animal.discard();
        continue;
      }
      pair.add(animal);
    }
    if (pair.size() < 2) return 0;
    for (var animal : pair)
      if (!w.addFreshEntity(animal)) {
        pair.forEach(Animal::discard);
        return 0;
      }
    return 2;
  }

  private static EntityType<? extends Animal> revivedType(String id) {
    return switch (id) {
      case "minecraft:cow" -> dev.reterra.compat.GameAccess.COW;
      case "minecraft:chicken" -> dev.reterra.compat.GameAccess.CHICKEN;
      case "minecraft:bee" -> dev.reterra.compat.GameAccess.BEE;
      default -> dev.reterra.compat.GameAccess.SHEEP;
    };
  }
}
