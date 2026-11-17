package dev.reterra.machine;

import dev.reterra.Content;
import dev.reterra.Journal;
import dev.reterra.ecology.Ecology;
import dev.reterra.effect.AnimationPayload.Kind;
import dev.reterra.effect.AnimationSignals;
import dev.reterra.state.RestorationState;
import dev.reterra.state.RestorationState.Machine;
import java.util.List;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public final class MachineLogic {
  private MachineLogic() {}

  public static final List<String> KINDS =
      List.of(
          "soil_reclaimer",
          "restoration_core",
          "water_restorer",
          "revival_chamber",
          "heart_of_the_world");

  public enum Status {
    READY,
    ASSEMBLY,
    REGISTER,
    OVERLAP,
    PAUSED,
    COMPOST,
    FUEL,
    WATER,
    MATERIAL,
    OUTPUT,
    HABITAT,
    SAMPLE,
    FOOD,
    NUTRIENTS,
    NO_SPACE,
    REVIVED,
    RECLAIMED,
    OBJECTIVES,
    COMPLETE,
    CULTURE,
    INCUBATING,
    POPULATION,
    STABILIZING,
    AWAKENING
  }

  public static int kind(Machine m) {
    return Math.max(0, KINDS.indexOf(m.kind));
  }

  public static boolean feedstock(ItemStack s) {
    return s.isOf(Items.GRAVEL) || s.isOf(Items.COARSE_DIRT);
  }

  public static boolean sample(ItemStack s) {
    return s.isOf(Content.SHEEP_SAMPLE)
        || s.isOf(Content.COW_SAMPLE)
        || s.isOf(Content.CHICKEN_SAMPLE)
        || s.isOf(Content.BEE_SAMPLE);
  }

  public static boolean accepts(int kind, int slot, ItemStack s) {
    return switch (slot) {
      case 0 ->
          kind == 2 ? s.isOf(Content.FILTER) : (kind == 0 || kind == 1) && s.isOf(Content.COMPOST);
      case 1 ->
          kind < 3 && (s.isOf(Items.COAL) || s.isOf(Items.CHARCOAL) || s.isOf(Content.NUTRIENTS));
      case 2 -> kind < 4 && s.isOf(Items.WATER_BUCKET);
      case 3 ->
          switch (kind) {
            case 0 -> feedstock(s);
            case 1 -> false;
            case 2 -> s.isOf(Items.BUCKET);
            case 3 -> sample(s);
            default -> false;
          };
      case 4 ->
          kind == 3
              && (s.isOf(Items.WHEAT)
                  || s.isOf(Items.WHEAT_SEEDS)
                  || dev.reterra.compat.WorldAccess.flower(s));
      case 5 -> kind == 3 && s.isOf(Content.NUTRIENTS);
      default -> false;
    };
  }

  public static boolean accepts(int kind, int slot, ItemStack stack, ItemStack selectedSample) {
    if (kind == 3 && slot == 4) return sample(selectedSample) && foodMatches(selectedSample, stack);
    return accepts(kind, slot, stack);
  }

  public static boolean visible(int kind, int slot) {
    return switch (slot) {
      case 0, 1 -> kind < 3;
      case 2 -> kind < 4;
      case 3 -> kind < 4 && kind != 1;
      case 4, 5 -> kind == 3;
      case 6 -> kind == 0 || kind == 2;
      case 7 -> false;
      default -> false;
    };
  }

  public static boolean room(Machine m, int slot, Item item, int count) {
    ItemStack old = m.getStack(slot);
    return old.isEmpty()
        ? count <= item.getMaxCount()
        : old.isOf(item) && old.getCount() + count <= old.getMaxCount();
  }

  public static void output(Machine m, int slot, Item item, int count) {
    if (m.getStack(slot).isEmpty()) m.setStack(slot, new ItemStack(item, count));
    else m.getStack(slot).increment(count);
    m.markDirty();
  }

  public static void stock(Machine m) {
    int kind = kind(m);
    if (kind < 3) {
      if (m.compost < 1 && accepts(kind, 0, m.getStack(0))) {
        m.removeStack(0, 1);
        m.compost += kind == 2 ? Balance.FILTER_WORK : Balance.COMPOST_WORK;
      }
      if (m.fuel < (kind == 2 ? 4 : 1) && accepts(kind, 1, m.getStack(1))) {
        boolean bio = m.getStack(1).isOf(Content.NUTRIENTS);
        m.removeStack(1, 1);
        m.fuel += bio ? Balance.BIOFUEL_WORK : Balance.COAL_WORK;
      }
    }
    if (kind < 4
        && m.water <= Balance.TANK_CAPACITY - Balance.WATER_WORK
        && m.getStack(2).isOf(Items.WATER_BUCKET)) {
      m.setStack(2, new ItemStack(Items.BUCKET));
      m.water += Balance.WATER_WORK;
    }
  }

  public static void refundCultures(ServerWorld world, Machine machine) {
    if (machine.culture <= 0) return;
    int capacity = EcosystemBenefits.culture(EcosystemBenefits.mask(RestorationState.get(world)));
    int count = (machine.culture + capacity - 1) / capacity;
    machine.culture = 0;
    ItemStack old = machine.getStack(3);
    if (old.isEmpty()) {
      int stored = Math.min(count, Content.CULTURE.getMaxCount());
      machine.setStack(3, new ItemStack(Content.CULTURE, stored));
      count -= stored;
    } else if (old.isOf(Content.CULTURE)) {
      int stored = Math.min(count, old.getMaxCount() - old.getCount());
      old.increment(stored);
      count -= stored;
    }
    if (count > 0)
      net.minecraft.util.ItemScatterer.spawn(
          world,
          machine.pos.getX() + .5,
          machine.pos.getY() + 1,
          machine.pos.getZ() + .5,
          new ItemStack(Content.CULTURE, count));
    machine.markDirty();
  }

  public static void stock(ServerWorld world, Machine m) {
    int compost = m.compost, fuel = m.fuel, culture = m.culture;
    stock(m);
    if (kind(m) < 2) {
      int mask = EcosystemBenefits.mask(RestorationState.get(world));
      if (m.compost > compost) m.compost += EcosystemBenefits.compost(mask) - Balance.COMPOST_WORK;
      if (m.fuel > fuel)
        m.fuel +=
            EcosystemBenefits.fuel(mask, m.fuel - fuel == Balance.BIOFUEL_WORK) - (m.fuel - fuel);
      if (m.culture > culture) m.culture += EcosystemBenefits.culture(mask) - Balance.CULTURE_WORK;
    }
  }

  public static boolean register(ServerWorld w, Machine m) {
    var state = RestorationState.get(w);
    for (Machine other : state.machines.values())
      if (other != m
          && other.kind.equals("restoration_core")
          && other.registered
          && Ecology.overlaps(
              m.pos.getX() >> 4, m.pos.getZ() >> 4, other.pos.getX() >> 4, other.pos.getZ() >> 4)) {
        m.feedback = Status.OVERLAP.ordinal();
        return false;
      }
    for (Machine other : state.reclaimedRegions.values())
      if (Ecology.overlaps(
          m.pos.getX() >> 4, m.pos.getZ() >> 4, other.pos.getX() >> 4, other.pos.getZ() >> 4)) {
        m.feedback = Status.OVERLAP.ordinal();
        return false;
      }
    m.registered = true;
    if (introAvailable(state, m)) m.introductory = true;
    m.feedback = 0;
    m.markDirty();
    return true;
  }

  public static boolean introAvailable(RestorationState state, Machine m) {
    return state.completed.isEmpty()
        && state.machines.values().stream()
            .noneMatch(other -> other != m && other.registered && other.introductory);
  }

  public static boolean overlaps(RestorationState state, Machine m) {
    return java.util.stream.Stream.concat(
            state.machines.values().stream(), state.reclaimedRegions.values().stream())
        .anyMatch(
            other ->
                other != m
                    && other.kind.equals("restoration_core")
                    && (other.registered || other.reclaimed)
                    && Ecology.overlaps(
                        m.pos.getX() >> 4,
                        m.pos.getZ() >> 4,
                        other.pos.getX() >> 4,
                        other.pos.getZ() >> 4));
  }

  public static Status status(ServerWorld w, Machine m) {
    if (m.reclaimed) return Status.RECLAIMED;
    if (Multiblocks.issue(w, m.pos, m.kind) > 0) return Status.ASSEMBLY;
    if (m.paused) return Status.PAUSED;
    int kind = kind(m);
    if (kind == 4) {
      var state = RestorationState.get(w);
      return state.heartActivated
          ? Status.COMPLETE
          : state.heartAwakening > 0 ? Status.AWAKENING : Status.OBJECTIVES;
    }
    if (kind == 1 && !m.registered)
      return m.feedback == Status.OVERLAP.ordinal() ? Status.OVERLAP : Status.REGISTER;
    if (kind == 3) {
      var status = habitatStatus(w, m);
      return status == Status.READY && !m.pendingSpecies.isEmpty() ? Status.INCUBATING : status;
    }
    if (kind == 1
        && java.util.Arrays.stream(m.sectors).allMatch(s -> s.surveyed)
        && m.survey(RestorationEngine.animalSpecies(w, m)).stage(m.family, m.introductory)
            == Ecology.Stage.RECLAIMED) return Status.STABILIZING;
    int soilTarget = Ecology.goals(m.family, m.introductory).soil();
    if (kind == 1 && m.survey(0).soilPercent() >= soilTarget) return Status.HABITAT;
    if (m.compost < 1
        && m.getStack(0).isEmpty()
        && (kind != 1 || m.survey(0).soilPercent() < soilTarget)) return Status.COMPOST;
    if (m.fuel < 1 && m.getStack(1).isEmpty()) return Status.FUEL;
    if (!hasWaterSupply(w, m)) return Status.WATER;
    if (kind == 0 && m.production && !feedstock(m.getStack(3))) {
      if (!(w.getBlockEntity(Multiblocks.barrel(w, m.pos))
              instanceof net.minecraft.inventory.Inventory inv)
          || !containsGravel(inv)) return Status.MATERIAL;
    }
    if (kind == 2) return Status.READY;
    if ((kind == 0 || kind == 2) && !room(m, 6, kind == 0 ? Items.DIRT : Items.WATER_BUCKET, 1))
      return Status.OUTPUT;
    return Status.READY;
  }

  private static boolean containsGravel(net.minecraft.inventory.Inventory inv) {
    for (int i = 0; i < inv.size(); i++) if (feedstock(inv.getStack(i))) return true;
    return false;
  }

  public static Status habitatStatus(ServerWorld w, Machine chamber) {
    Machine core = RestorationEngine.regionAt(RestorationState.get(w), chamber.pos);
    if (core == null) return Status.REGISTER;
    var survey = core.survey(RestorationEngine.animalCount(w, core));
    if (!survey.habitat(core.family) || survey.trees() < 1 || survey.flowers() < 1)
      return Status.HABITAT;
    if (RestorationEngine.animalCount(w, core) >= 16) return Status.POPULATION;
    if (!sample(chamber.getStack(3))) return Status.SAMPLE;
    if (!foodMatches(chamber.getStack(3), chamber.getStack(4))
        || chamber.getStack(4).getCount() < foodCount(chamber.getStack(3))) return Status.FOOD;
    if (!chamber.getStack(5).isOf(Content.NUTRIENTS) || chamber.getStack(5).getCount() < 1)
      return Status.NUTRIENTS;
    if (chamber.water < 16 && !chamber.getStack(2).isOf(Items.WATER_BUCKET)) return Status.WATER;
    return Status.READY;
  }

  public static Item food(ItemStack sample) {
    return sample.isOf(Content.BEE_SAMPLE)
        ? Items.DANDELION
        : sample.isOf(Content.CHICKEN_SAMPLE) ? Items.WHEAT_SEEDS : Items.WHEAT;
  }

  public static int foodCount(ItemStack sample) {
    return sample.isOf(Content.BEE_SAMPLE) ? 2 : 4;
  }

  public static boolean foodMatches(ItemStack sample, ItemStack food) {
    return sample.isOf(Content.BEE_SAMPLE)
        ? dev.reterra.compat.WorldAccess.flower(food)
        : food.isOf(food(sample));
  }

  public static void action(ServerWorld w, Machine m, ServerPlayerEntity player, int button) {
    if (button == 3 && m.kind.equals("revival_chamber")) {
      m.pendingSpecies = "";
      m.revivalTicks = 0;
      m.feedback = 0;
      m.markDirty();
      return;
    }
    if (button == 0 && m.kind.equals("soil_reclaimer")) {
      m.production = !m.production;
      m.markDirty();
      return;
    }
    if (button == 0 && m.kind.equals("water_restorer")) {
      m.riverMode = !m.riverMode;
      m.markDirty();
      return;
    }
    if (button == 2) {
      m.paused = !m.paused;
      m.markDirty();
      return;
    }
    if (button != 1 || Multiblocks.issue(w, m.pos, m.kind) > 0 || m.reclaimed || m.paused) return;
    m.feedback = 0;
    switch (m.kind) {
      case "restoration_core" -> {
        if (register(w, m)) {
          m.caretakers.add(player.getUuid());
          Journal.grant(player, "greener_world");
        }
      }
      case "water_restorer" -> {
        stock(m);
        int before = m.water;
        water(m);
        if (m.water < before) AnimationSignals.emit(w, m.pos, m.pos, Kind.WATER, 0);
      }
      case "revival_chamber" -> revive(w, m, player);
      case "heart_of_the_world" -> {
        if (dev.reterra.world.HeartAwakening.start(w, m)) player.closeHandledScreen();
      }
    }
  }

  public static void water(Machine m) {
    if (m.paused
        || m.compost < 1
        || m.fuel < 4
        || m.water < Balance.WATER_WORK
        || !m.getStack(3).isOf(Items.BUCKET)
        || !room(m, 6, Items.WATER_BUCKET, 1)) return;
    m.removeStack(3, 1);
    output(m, 6, Items.WATER_BUCKET, 1);
    m.compost--;
    m.fuel -= 4;
    m.water -= Balance.WATER_WORK;
    m.markDirty();
  }

  private static void revive(ServerWorld w, Machine m, ServerPlayerEntity p) {
    if (!m.pendingSpecies.isEmpty()) return;
    stock(m);
    if (habitatStatus(w, m) != Status.READY) return;
    RestorationEngine.regionAt(RestorationState.get(w), m.pos).caretakers.add(p.getUuid());
    m.pendingSpecies = Registries.ITEM.getId(m.getStack(3).getItem()).getPath();
    m.revivalTicks = 0;
    m.feedback = Status.INCUBATING.ordinal();
    m.markDirty();
  }

  public static void revivalTick(ServerWorld w, Machine m) {
    if (m.pendingSpecies.isEmpty() || habitatStatus(w, m) != Status.READY) return;
    m.revivalTicks = Math.min(Balance.REVIVAL_TICKS, m.revivalTicks + 5);
    AnimationSignals.emit(
        w, m.pos, m.pos, Kind.REVIVAL, (float) m.revivalTicks / Balance.REVIVAL_TICKS);
    if (m.revivalTicks < Balance.REVIVAL_TICKS && m.revivalTicks % 40 == 0)
      w.playSound(
          null,
          m.pos,
          net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
          net.minecraft.sound.SoundCategory.BLOCKS,
          .3f,
          .8f + m.revivalTicks / 600f);
    if (m.revivalTicks < Balance.REVIVAL_TICKS) return;
    var state = RestorationState.get(w);
    Machine core = RestorationEngine.regionAt(state, m.pos);
    if (RestorationEngine.animalCount(w, core) >= 16) return;
    ItemStack sample = m.getStack(3);
    EntityType<? extends AnimalEntity> type =
        sample.isOf(Content.BEE_SAMPLE)
            ? EntityType.BEE
            : sample.isOf(Content.CHICKEN_SAMPLE)
                ? EntityType.CHICKEN
                : sample.isOf(Content.COW_SAMPLE) ? EntityType.COW : EntityType.SHEEP;
    if (RestorationEngine.spawnPair(w, core, type, m.pos) == 0) {
      m.feedback = Status.NO_SPACE.ordinal();
      return;
    }
    int foodCost = foodCount(sample);
    dev.reterra.world.ArchiveSamples.record(state, sample);
    m.removeStack(3, 1);
    m.removeStack(4, foodCost);
    m.removeStack(5, 1);
    m.water -= 16;
    m.pendingSpecies = "";
    m.revivalTicks = 0;
    core.restoredSpecies.add(Registries.ENTITY_TYPE.getId(type).toString());
    m.feedback = Status.REVIVED.ordinal();
    AnimationSignals.emit(w, m.pos, m.pos, Kind.REVIVED, 0);
    w.playSound(
        null,
        m.pos,
        net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
        net.minecraft.sound.SoundCategory.BLOCKS,
        .5f,
        1.1f);
    state.markDirty();
    w.getPlayers().stream()
        .filter(p -> RestorationEngine.bounds(core).contains(dev.reterra.compat.WorldAccess.position(p)))
        .forEach(p -> Journal.grant(p, "natures_return"));
  }

  public static boolean hasWaterSupply(ServerWorld w, Machine machine) {
    return machine.water > 0
        || machine.getStack(2).isOf(Items.WATER_BUCKET)
        || machine.kind.equals("water_restorer")
            && (pondIntake(w, machine) || intakeCauldron(w, machine) != null);
  }

  private static boolean pondIntake(ServerWorld w, Machine pump) {
    int reach = EcosystemBenefits.pumpReach(EcosystemBenefits.mask(RestorationState.get(w)));
    int intake = reach == 16 ? 4 : 2;
    for (int dx = -intake; dx <= intake; dx++)
      for (int dz = -intake; dz <= intake; dz++)
        for (int dy = -1; dy <= 0; dy++) {
          var p = pump.pos.add(dx, dy, dz);
          if (RestorationEngine.loaded(w, p)
              && w.getBlockState(p).isOf(net.minecraft.block.Blocks.WATER)
              && w.getFluidState(p).isStill()) return true;
        }
    return false;
  }

  private static net.minecraft.util.math.BlockPos intakeCauldron(ServerWorld w, Machine pump) {
    for (var direction : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
      var pos = pump.pos.offset(direction);
      if (!RestorationEngine.loaded(w, pos)) continue;
      var cauldron = w.getBlockState(pos);
      if (cauldron.isOf(net.minecraft.block.Blocks.WATER_CAULDRON)
          && cauldron.get(net.minecraft.block.LeveledCauldronBlock.LEVEL) == 3) return pos;
    }
    return null;
  }

  public static void pump(ServerWorld w, Machine pump) {
    int reach = EcosystemBenefits.pumpReach(EcosystemBenefits.mask(RestorationState.get(w)));
    if (pump.water < 32 && pump.fuel > 0 && pump.compost > 0 && pondIntake(w, pump))
      pump.water += Balance.WATER_WORK;
    // A full intake cauldron replenishes the tank; manual buckets work as well.
    if (pump.water < 32) {
      var pos = intakeCauldron(w, pump);
      if (pos != null) {
        pump.water += Balance.WATER_WORK;
        w.setBlockState(
            pos, w.getBlockState(pos).with(net.minecraft.block.LeveledCauldronBlock.LEVEL, 1));
      }
    }
    if (pump.water < 1 || pump.fuel < 1 || pump.compost < 1) return;
    if (pump.riverMode) {
      dev.reterra.world.RiverRecovery.tick(w, pump);
      return;
    }
    boolean delivered = false;
    for (Machine other : RestorationState.get(w).machines.values()) {
      if (other == pump
          || other.reclaimed
          || other.kind.equals("water_restorer")
          || other.kind.equals("heart_of_the_world")
          || other.water >= 32
          || other.pos.getSquaredDistance(pump.pos) > reach * reach
          || !RestorationEngine.loaded(w, other.pos)) continue;
      int amount = Math.min(32, pump.water);
      other.water += amount;
      pump.water -= amount;
      pump.fuel--;
      pump.compost--;
      other.markDirty();
      AnimationSignals.emit(w, pump.pos, other.pos, Kind.WATER, 0);
      delivered = true;
      break;
    }
    int beforeBottling = pump.water;
    water(pump);
    if (pump.water < beforeBottling && !delivered)
      AnimationSignals.emit(w, pump.pos, pump.pos, Kind.WATER, 0);
    pump.markDirty();
  }
}
