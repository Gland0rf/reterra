package dev.reterra.screen;

import dev.reterra.Content;
import dev.reterra.ecology.Ecology;
import dev.reterra.machine.MachineLogic;
import dev.reterra.machine.Multiblocks;
import dev.reterra.machine.RestorationEngine;
import dev.reterra.state.RestorationState;
import dev.reterra.state.RestorationState.Machine;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class MachineScreenHandler extends AbstractContainerMenu {
  public static final int PROPERTY_COUNT = 54;
  private final java.util.UUID viewer;
  public static final MenuType<MachineScreenHandler> TYPE =
      Registry.register(
          BuiltInRegistries.MENU,
          Content.id("machine"),
          new MenuType<>(MachineScreenHandler::new, FeatureFlags.VANILLA_SET));
  public static final int[] SLOT_X = {16, 64, 112, 160, 196, 232, 256, 278};
  private final Container inventory;
  private final ContainerData data;
  private final Machine machine;
  private final ServerLevel world;
  private final boolean remote;
  private boolean preview;
  private long previewTick = Long.MIN_VALUE;
  private boolean introductory;
  private int page;
  private Ecology.Family surveyFamily = Ecology.Family.PLAINS;
  private long surveyTime = Long.MIN_VALUE;
  private int animalTypes, surveyedSectors;
  private int plantVariant;
  private boolean sectorsLoaded;
  private Ecology.Survey cachedSurvey = new Ecology.Survey(0, 0, 0, 0, 0, 0, 0, 0, 0);

  public static void initialize() {}

  public MachineScreenHandler(int syncId, Inventory player) {
    this(
        syncId,
        player,
        new SimpleContainer(8),
        new SimpleContainerData(PROPERTY_COUNT),
        null,
        null,
        false);
  }

  public MachineScreenHandler(
      int syncId, Inventory player, ServerLevel world, Machine machine, boolean remote) {
    this(syncId, player, machine, null, world, machine, remote);
  }

  private MachineScreenHandler(
      int syncId,
      Inventory player,
      Container inventory,
      ContainerData delegate,
      ServerLevel world,
      Machine machine,
      boolean remote) {
    super(TYPE, syncId);
    this.inventory = inventory;
    this.world = world;
    this.machine = machine;
    this.remote = remote;
    this.viewer = player.player == null ? null : player.player.getUUID();
    if (world != null
        && machine != null
        && !machine.reclaimed
        && Multiblocks.issue(world, machine.pos, machine.kind) > 0) page = 1;
    data =
        delegate == null
            ? new ContainerData() {
              @Override
              public int getCount() {
                return PROPERTY_COUNT;
              }

              @Override
              public void set(int index, int value) {}

              @Override
              public int get(int index) {
                return serverValue(index);
              }
            }
            : delegate;
    for (int i = 0; i < 8; i++) {
      final int slot = i;
      addSlot(
          new Slot(inventory, i, SLOT_X[i], 88) {
            @Override
            public boolean mayPlace(ItemStack stack) {
              return !readOnly()
                  && value(20) == 0
                  && !(kind() == 3 && value(33) > 0 && slot >= 3 && slot <= 5)
                  && MachineLogic.accepts(kind(), slot, stack, container.getItem(3));
            }

            @Override
            public boolean mayPickup(Player p) {
              return !readOnly() && !(kind() == 3 && value(33) > 0 && slot >= 3 && slot <= 5);
            }

            @Override
            public boolean isActive() {
              return value(26) == 0 && supplyVisible(slot);
            }
          });
    }
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(player, col + row * 9 + 9, 69 + col * 18, 156 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(player, col, 69 + col * 18, 214));
    addDataSlots(data);
  }

  public int kind() {
    return value(0);
  }

  public int value(int i) {
    return data.get(i);
  }

  public boolean readOnly() {
    return value(27) != 0;
  }

  public boolean supplyVisible(int slot) {
    return MachineLogic.visible(kind(), slot)
        || (slot == 7 || kind() == 1 && slot == 3) && !inventory.getItem(slot).isEmpty();
  }

  public String kindName() {
    return MachineLogic.KINDS.get(Math.max(0, Math.min(kind(), 4)));
  }

  private int serverValue(int i) {
    if (world.getGameTime() != surveyTime) {
      surveyTime = world.getGameTime();
      Machine core =
          machine.kind.equals("restoration_core")
              ? machine
              : RestorationEngine.regionAt(RestorationState.get(world), machine.pos);
      surveyFamily = core == null ? machine.family : core.family;
      plantVariant =
          dev.reterra.ecology.HabitatPlants.variant(core == null ? machine.biome : core.biome);
      if (preview)
        machine.introductory = MachineLogic.introAvailable(RestorationState.get(world), machine);
      introductory = core != null && core.introductory;
      if (preview && (previewTick == Long.MIN_VALUE || world.getGameTime() - previewTick >= 10)) {
        RestorationEngine.surveySector(world, machine, Math.floorMod(machine.sectorCursor++, 9));
        previewTick = world.getGameTime();
      }
      cachedSurvey =
          core == null
              ? new Ecology.Survey(0, 0, 0, 0, 0, 0, 0, 0, 0)
              : core.survey(RestorationEngine.animalCount(world, core));
      animalTypes = core == null ? 0 : RestorationEngine.animalSpecies(world, core);
      surveyedSectors =
          core == null
              ? 0
              : (int) java.util.Arrays.stream(core.sectors).filter(s -> s.surveyed).count();
      sectorsLoaded = core != null && RestorationEngine.allSectorsLoaded(world, core);
    }
    var s = cachedSurvey;
    var state = RestorationState.get(world);
    return switch (i) {
      case 0 -> MachineLogic.kind(machine);
      case 1 -> machine.compost;
      case 2 -> machine.fuel;
      case 3 -> machine.water;
      case 4 -> machine.culture;
      case 5 -> machine.production ? 1 : 0;
      case 6 -> machine.paused ? 1 : 0;
      case 7 -> machine.registered ? 1 : 0;
      case 8 ->
          preview
              ? MachineLogic.Status.READY.ordinal()
              : MachineLogic.status(world, machine).ordinal();
      case 9 ->
          preview || machine.reclaimed ? 0 : Multiblocks.issue(world, machine.pos, machine.kind);
      case 10 -> s.soilPercent();
      case 11 -> s.grassPercent();
      case 12 -> s.water();
      case 13 -> s.crops();
      case 14 -> s.trees();
      case 15 -> s.flowers();
      case 16 -> s.species();
      case 17 -> s.animals();
      case 18 -> s.sampled();
      case 19 ->
          state.completed.stream().mapToInt(f -> 1 << f.ordinal()).reduce(0, (a, b) -> a | b);
      case 20 -> machine.reclaimed ? 1 : 0;
      case 21 -> state.heartActivated ? 1 : 0;
      case 22 ->
          new Ecology.Survey(
                  s.sampled(),
                  s.fertile(),
                  s.grass(),
                  s.water(),
                  s.crops(),
                  s.trees(),
                  s.flowers(),
                  s.species(),
                  animalTypes,
                  s.treeTypes(),
                  s.wetland(),
                  s.slopes())
              .stage(surveyFamily, introductory)
              .ordinal();
      case 23 -> surveyFamily.ordinal();
      case 24 -> machine.feedback;
      case 25 -> state.completed.size();
      case 26 -> page;
      case 27 -> remote ? 1 : 0;
      case 28 -> machine.stableSeconds;
      case 29 -> animalTypes;
      case 30 -> surveyedSectors;
      case 31 -> sectorsLoaded ? 1 : 0;
      case 32 -> preview ? 0 : Multiblocks.rotation(world, machine.pos, machine.kind);
      case 33 -> machine.pendingSpecies.isEmpty() ? 0 : machine.revivalTicks + 1;
      case 34 -> machine.spreadRadius;
      case 35 -> machine.spreadTicks / 20;
      case 36 -> s.treeTypes();
      case 37 -> s.wetland();
      case 38 -> s.slopes();
      case 39 -> machine.riverMode ? 1 : 0;
      case 40 -> machine.riverRestored;
      case 41 -> state.completed.isEmpty() ? 0 : 1;
      case 42 ->
          (preview
                  ? state.previewBorders.containsKey(viewer)
                  : state.boundaryViewers.contains(viewer))
              ? 1
              : 0;
      case 43 -> introductory ? 1 : 0;
      case 44 -> Ecology.stabilityTarget(introductory);
      case 45 -> preview ? 1 : 0;
      case 46 ->
          (machine.careTasks.contains("tend") ? 1 : 0)
              | (machine.careTasks.contains("plant") ? 2 : 0)
              | (machine.careTasks.contains("feed") ? 4 : 0);
      case 47 -> machine.careTasks.size();
      case 48 ->
          Math.max(
              0,
              (s.sampled() * Ecology.goals(surveyFamily, introductory).soil() + 99) / 100
                  - s.fertile());
      case 49 ->
          Math.max(
              0,
              (s.sampled() * Ecology.goals(surveyFamily, introductory).grass() + 99) / 100
                  - s.grass());
      case 50 ->
          Math.max(
              0,
              serverValue(48)
                  - java.util.Arrays.stream(machine.sectors).mapToInt(v -> v.lastRestorable).sum());
      case 51 -> MachineLogic.overlaps(state, machine) ? 1 : 0;
      case 52 -> plantVariant;
      case 53 -> state.heartAwakening;
      default -> 0;
    };
  }

  @Override
  public boolean stillValid(Player player) {
    if (world == null) return true;
    if (preview)
      return dev.reterra.compat.WorldAccess.entityWorld(player) == world
          && !player.isSpectator()
          && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(machine.pos))
              < 128 * 128
          && (player.getMainHandItem().is(Content.SCANNER)
              || player.getOffhandItem().is(Content.SCANNER));
    if (dev.reterra.compat.WorldAccess.entityWorld(player) != world
        || !dev.reterra.compat.WorldAccess.canModify(world, player, machine.pos)
        || player.isSpectator()) return false;
    var state = RestorationState.get(world);
    if (state.machines.get(machine.pos.asLong()) != machine
        && state.reclaimedRegions.get(machine.pos.asLong()) != machine) return false;
    if (remote)
      return RestorationEngine.regionAt(state, player.blockPosition()) == machine
          && (player.getMainHandItem().is(Content.SCANNER)
              || player.getOffhandItem().is(Content.SCANNER));
    return machine.stillValid(player)
        && Content.isMachine(world.getBlockState(machine.pos).getBlock());
  }

  @Override
  public boolean clickMenuButton(Player player, int id) {
    if (!stillValid(player)) return false;
    if (id >= 10 && id <= 13) {
      if (preview && id < 12) return false;
      page = id - 10;
      broadcastChanges();
      return true;
    }
    if (id == 4 && world != null && machine.kind.equals("restoration_core")) {
      if (preview) {
        var borders = RestorationState.get(world).previewBorders;
        if (borders.remove(player.getUUID()) == null)
          borders.put(
              player.getUUID(),
              new RestorationState.PreviewBorder(machine.pos, world.getGameTime() + 600));
        broadcastChanges();
        return true;
      }
      var viewers = RestorationState.get(world).boundaryViewers;
      if (!viewers.remove(player.getUUID())) viewers.add(player.getUUID());
      broadcastChanges();
      return true;
    }
    if (world == null || readOnly() || !(player instanceof ServerPlayer server)) return false;
    MachineLogic.action(world, machine, server, id);
    broadcastChanges();
    return true;
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
    ItemStack source = slot.getItem();
    ItemStack copy = source.copy();
    if (index < 8) {
      if (readOnly() || !moveItemStackTo(source, 8, 44, true)) return ItemStack.EMPTY;
    } else {
      if (readOnly()) return ItemStack.EMPTY;
      boolean inserted = false;
      for (int i = 0; i < 6; i++)
        if (MachineLogic.accepts(kind(), i, source, inventory.getItem(3))
            && moveItemStackTo(source, i, i + 1, false)) {
          inserted = true;
          break;
        }
      if (!inserted) return ItemStack.EMPTY;
    }
    if (source.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
    else slot.setChanged();
    slot.onTake(player, source);
    return copy;
  }

  public static void open(ServerPlayer player, Machine machine, boolean remote) {
    ServerLevel world = dev.reterra.compat.WorldAccess.world(player);
    player.openMenu(
        new SimpleMenuProvider(
            (id, inv, p) -> new MachineScreenHandler(id, inv, world, machine, remote),
            Component.translatable("block.reterra." + machine.kind)));
  }

  public static MachineScreenHandler previewHandler(
      int id, Inventory inventory, ServerLevel world, net.minecraft.core.BlockPos pos) {
    Machine machine = new Machine(pos, "restoration_core");
    machine.sectorCursor = 4;
    machine.biome = world.getBiome(pos).unwrapKey().orElseThrow().identifier().toString();
    machine.family = dev.reterra.world.BarrenWorld.family(machine.biome);
    machine.introductory = MachineLogic.introAvailable(RestorationState.get(world), machine);
    MachineScreenHandler handler = new MachineScreenHandler(id, inventory, world, machine, true);
    handler.preview = true;
    handler.page = 2;
    return handler;
  }

  public static void preview(ServerPlayer player, net.minecraft.core.BlockPos pos) {
    if (!dev.reterra.compat.WorldAccess.world(player).dimension().equals(net.minecraft.world.level.Level.OVERWORLD))
      return;
    player.openMenu(
        new SimpleMenuProvider(
            (id, inv, p) -> previewHandler(id, inv, dev.reterra.compat.WorldAccess.world(player), pos),
            Component.literal("Restoration site survey")));
  }
}
