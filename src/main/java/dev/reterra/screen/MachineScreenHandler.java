package dev.reterra.screen;

import dev.reterra.Content;
import dev.reterra.ecology.Ecology;
import dev.reterra.machine.MachineLogic;
import dev.reterra.machine.Multiblocks;
import dev.reterra.machine.RestorationEngine;
import dev.reterra.state.RestorationState;
import dev.reterra.state.RestorationState.Machine;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public final class MachineScreenHandler extends ScreenHandler {
  public static final int PROPERTY_COUNT = 54;
  private final java.util.UUID viewer;
  public static final ScreenHandlerType<MachineScreenHandler> TYPE =
      Registry.register(
          Registries.SCREEN_HANDLER,
          Content.id("machine"),
          new ScreenHandlerType<>(MachineScreenHandler::new, FeatureFlags.VANILLA_FEATURES));
  public static final int[] SLOT_X = {16, 64, 112, 160, 196, 232, 256, 278};
  private final Inventory inventory;
  private final PropertyDelegate data;
  private final Machine machine;
  private final ServerWorld world;
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

  public MachineScreenHandler(int syncId, PlayerInventory player) {
    this(
        syncId,
        player,
        new SimpleInventory(8),
        new ArrayPropertyDelegate(PROPERTY_COUNT),
        null,
        null,
        false);
  }

  public MachineScreenHandler(
      int syncId, PlayerInventory player, ServerWorld world, Machine machine, boolean remote) {
    this(syncId, player, machine, null, world, machine, remote);
  }

  private MachineScreenHandler(
      int syncId,
      PlayerInventory player,
      Inventory inventory,
      PropertyDelegate delegate,
      ServerWorld world,
      Machine machine,
      boolean remote) {
    super(TYPE, syncId);
    this.inventory = inventory;
    this.world = world;
    this.machine = machine;
    this.remote = remote;
    this.viewer = player.player == null ? null : player.player.getUuid();
    if (world != null
        && machine != null
        && !machine.reclaimed
        && Multiblocks.issue(world, machine.pos, machine.kind) > 0) page = 1;
    data =
        delegate == null
            ? new PropertyDelegate() {
              @Override
              public int size() {
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
            public boolean canInsert(ItemStack stack) {
              return !readOnly()
                  && value(20) == 0
                  && !(kind() == 3 && value(33) > 0 && slot >= 3 && slot <= 5)
                  && MachineLogic.accepts(kind(), slot, stack, inventory.getStack(3));
            }

            @Override
            public boolean canTakeItems(PlayerEntity p) {
              return !readOnly() && !(kind() == 3 && value(33) > 0 && slot >= 3 && slot <= 5);
            }

            @Override
            public boolean isEnabled() {
              return value(26) == 0 && supplyVisible(slot);
            }
          });
    }
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(player, col + row * 9 + 9, 69 + col * 18, 156 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(player, col, 69 + col * 18, 214));
    addProperties(data);
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
        || (slot == 7 || kind() == 1 && slot == 3) && !inventory.getStack(slot).isEmpty();
  }

  public String kindName() {
    return MachineLogic.KINDS.get(Math.max(0, Math.min(kind(), 4)));
  }

  private int serverValue(int i) {
    if (world.getTime() != surveyTime) {
      surveyTime = world.getTime();
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
      if (preview && (previewTick == Long.MIN_VALUE || world.getTime() - previewTick >= 10)) {
        RestorationEngine.surveySector(world, machine, Math.floorMod(machine.sectorCursor++, 9));
        previewTick = world.getTime();
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
  public boolean canUse(PlayerEntity player) {
    if (world == null) return true;
    if (preview)
      return dev.reterra.compat.WorldAccess.entityWorld(player) == world
          && !player.isSpectator()
          && player.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(machine.pos))
              < 128 * 128
          && (player.getMainHandStack().isOf(Content.SCANNER)
              || player.getOffHandStack().isOf(Content.SCANNER));
    if (dev.reterra.compat.WorldAccess.entityWorld(player) != world
        || !dev.reterra.compat.WorldAccess.canModify(world, player, machine.pos)
        || player.isSpectator()) return false;
    var state = RestorationState.get(world);
    if (state.machines.get(machine.pos.asLong()) != machine
        && state.reclaimedRegions.get(machine.pos.asLong()) != machine) return false;
    if (remote)
      return RestorationEngine.regionAt(state, player.getBlockPos()) == machine
          && (player.getMainHandStack().isOf(Content.SCANNER)
              || player.getOffHandStack().isOf(Content.SCANNER));
    return machine.canPlayerUse(player)
        && Content.isMachine(world.getBlockState(machine.pos).getBlock());
  }

  @Override
  public boolean onButtonClick(PlayerEntity player, int id) {
    if (!canUse(player)) return false;
    if (id >= 10 && id <= 13) {
      if (preview && id < 12) return false;
      page = id - 10;
      sendContentUpdates();
      return true;
    }
    if (id == 4 && world != null && machine.kind.equals("restoration_core")) {
      if (preview) {
        var borders = RestorationState.get(world).previewBorders;
        if (borders.remove(player.getUuid()) == null)
          borders.put(
              player.getUuid(),
              new RestorationState.PreviewBorder(machine.pos, world.getTime() + 600));
        sendContentUpdates();
        return true;
      }
      var viewers = RestorationState.get(world).boundaryViewers;
      if (!viewers.remove(player.getUuid())) viewers.add(player.getUuid());
      sendContentUpdates();
      return true;
    }
    if (world == null || readOnly() || !(player instanceof ServerPlayerEntity server)) return false;
    MachineLogic.action(world, machine, server, id);
    sendContentUpdates();
    return true;
  }

  @Override
  public ItemStack quickMove(PlayerEntity player, int index) {
    if (!canUse(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasStack() || !slot.canTakeItems(player)) return ItemStack.EMPTY;
    ItemStack source = slot.getStack();
    ItemStack copy = source.copy();
    if (index < 8) {
      if (readOnly() || !insertItem(source, 8, 44, true)) return ItemStack.EMPTY;
    } else {
      if (readOnly()) return ItemStack.EMPTY;
      boolean inserted = false;
      for (int i = 0; i < 6; i++)
        if (MachineLogic.accepts(kind(), i, source, inventory.getStack(3))
            && insertItem(source, i, i + 1, false)) {
          inserted = true;
          break;
        }
      if (!inserted) return ItemStack.EMPTY;
    }
    if (source.isEmpty()) slot.setStack(ItemStack.EMPTY);
    else slot.markDirty();
    slot.onTakeItem(player, source);
    return copy;
  }

  public static void open(ServerPlayerEntity player, Machine machine, boolean remote) {
    ServerWorld world = dev.reterra.compat.WorldAccess.world(player);
    player.openHandledScreen(
        new SimpleNamedScreenHandlerFactory(
            (id, inv, p) -> new MachineScreenHandler(id, inv, world, machine, remote),
            Text.translatable("block.reterra." + machine.kind)));
  }

  public static MachineScreenHandler previewHandler(
      int id, PlayerInventory inventory, ServerWorld world, net.minecraft.util.math.BlockPos pos) {
    Machine machine = new Machine(pos, "restoration_core");
    machine.sectorCursor = 4;
    machine.biome = world.getBiome(pos).getKey().orElseThrow().getValue().toString();
    machine.family = dev.reterra.world.BarrenWorld.family(machine.biome);
    machine.introductory = MachineLogic.introAvailable(RestorationState.get(world), machine);
    MachineScreenHandler handler = new MachineScreenHandler(id, inventory, world, machine, true);
    handler.preview = true;
    handler.page = 2;
    return handler;
  }

  public static void preview(ServerPlayerEntity player, net.minecraft.util.math.BlockPos pos) {
    if (!dev.reterra.compat.WorldAccess.world(player).getRegistryKey().equals(net.minecraft.world.World.OVERWORLD))
      return;
    player.openHandledScreen(
        new SimpleNamedScreenHandlerFactory(
            (id, inv, p) -> previewHandler(id, inv, dev.reterra.compat.WorldAccess.world(player), pos),
            Text.literal("Restoration site survey")));
  }
}
