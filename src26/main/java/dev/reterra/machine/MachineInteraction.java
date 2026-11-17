package dev.reterra.machine;

import dev.reterra.Content;
import dev.reterra.Journal;
import dev.reterra.screen.MachineScreenHandler;
import dev.reterra.state.RestorationState;
import dev.reterra.world.BarrenWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public final class MachineInteraction {
  private MachineInteraction() {}

  public static InteractionResult use(
      Player player, Level world, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
    if (player.isSpectator()) return InteractionResult.PASS;
    BlockPos pos = hit.getBlockPos();
    ItemStack held = player.getItemInHand(hand);
    if (held.is(Content.SCANNER)) {
      if (player instanceof ServerPlayer server) {
        var core = RestorationEngine.regionAt(RestorationState.get(dev.reterra.compat.WorldAccess.world(server)), pos);
        if (core == null) MachineScreenHandler.preview(server, pos.above());
        else if (player.isShiftKeyDown()) dev.reterra.ReTerra.inspect(server);
        else MachineScreenHandler.open(server, core, true);
      }
      return InteractionResult.SUCCESS;
    }
    if (held.is(Content.COMPOST)
        && world.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.CropBlock crop) {
      if (!dev.reterra.compat.WorldAccess.canModify(world, player, pos) || !player.mayBuild())
        return InteractionResult.FAIL;
      var planted = world.getBlockState(pos);
      if (crop.isMaxAge(planted)) return InteractionResult.PASS;
      if (world instanceof ServerLevel server) {
        world.setBlockAndUpdate(
            pos, crop.getStateForAge(Math.min(crop.getMaxAge(), crop.getAge(planted) + 2)));
        consume(player, held, 1);
        Stewardship.credit(server, pos, "tend");
        dev.reterra.effect.AnimationSignals.emit(
            server, pos, pos, dev.reterra.effect.AnimationPayload.Kind.GROWTH, 0);
      }
      return InteractionResult.SUCCESS;
    }
    if ((held.is(Content.COMPOST) || held.is(Items.BONE_MEAL))
        && world.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
      if (!dev.reterra.compat.WorldAccess.canModify(world, player, pos) || !player.mayBuild())
        return InteractionResult.FAIL;
      if (world instanceof ServerLevel server) {
        int grown = 0;
        for (int dx = -2; dx <= 2 && grown < 5; dx++)
          for (int dz = -2; dz <= 2 && grown < 5; dz++) {
            BlockPos flower = pos.offset(dx, 1, dz);
            if (!RestorationEngine.loaded(server, flower)
                || !dev.reterra.compat.WorldAccess.canModify(world, player, flower)
                || !world.getBlockState(flower.below()).is(Blocks.GRASS_BLOCK)
                || !world.getBlockState(flower).isAir()) continue;
            var core = RestorationEngine.regionAt(RestorationState.get(server), pos);
            String biome =
                core == null
                    ? world.getBiome(pos).unwrapKey().orElseThrow().identifier().toString()
                    : core.biome;
            var palette =
                dev.reterra.ecology.HabitatPlants.flowers(
                    core == null ? BarrenWorld.family(biome) : core.family,
                    dev.reterra.ecology.HabitatPlants.variant(biome));
            var plant = palette.get(grown % palette.size()).defaultBlockState();
            if (!plant.canSurvive(world, flower)) continue;
            world.setBlockAndUpdate(flower, plant);
            grown++;
            Stewardship.plant(server, flower);
          }
        if (grown > 0) {
          consume(player, held, 1);
          dev.reterra.effect.AnimationSignals.emit(
              server, pos, pos, dev.reterra.effect.AnimationPayload.Kind.GROWTH, 1);
          return InteractionResult.SUCCESS;
        }
      } else return InteractionResult.SUCCESS;
    }
    boolean culture = held.is(Content.CULTURE) && world.getBlockState(pos).is(Blocks.DIRT);
    boolean collect =
        held.is(Items.GLASS_BOTTLE) && world.getBlockState(pos).is(Blocks.GRASS_BLOCK);
    if (culture || collect) {
      if (!dev.reterra.compat.WorldAccess.canModify(world, player, pos) || !player.mayBuild())
        return InteractionResult.FAIL;
      if (player instanceof ServerPlayer server) {
        if (culture) {
          int grown = 0;
          for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
              BlockPos seed = pos.offset(dx, 0, dz);
              if (RestorationEngine.loaded(dev.reterra.compat.WorldAccess.world(server), seed)
                  && dev.reterra.compat.WorldAccess.canModify(world, player, seed)
                  && world.getBlockState(seed).is(Blocks.DIRT)
                  && !dev.reterra.compat.WorldAccess.opaque(world, seed.above())
                  && world.getFluidState(seed.above()).isEmpty()) {
                world.setBlockAndUpdate(seed, Blocks.GRASS_BLOCK.defaultBlockState());
                grown++;
              }
            }
          if (grown == 0) return InteractionResult.FAIL;
          dev.reterra.effect.AnimationSignals.emit(
              dev.reterra.compat.WorldAccess.world(server),
              pos,
              pos,
              dev.reterra.effect.AnimationPayload.Kind.GROWTH,
              1);
          consume(player, held, 1);
          if (!player.isCreative()) give(player, new ItemStack(Items.GLASS_BOTTLE));
          Journal.grant(server, "first_growth");
        } else {
          consume(player, held, 1);
          give(player, new ItemStack(Content.CULTURE));
        }
      }
      return InteractionResult.SUCCESS;
    }
    var block = world.getBlockState(pos).getBlock();
    if (!Content.isMachine(block)) return InteractionResult.PASS;
    if (player.isShiftKeyDown() && held.getItem() instanceof BlockItem) return InteractionResult.PASS;
    if (!dev.reterra.compat.WorldAccess.canModify(world, player, pos) || !player.mayBuild())
      return InteractionResult.FAIL;
    if (world.isClientSide()) return InteractionResult.SUCCESS;
    if (!(world instanceof ServerLevel server) || !world.dimension().equals(Level.OVERWORLD))
      return InteractionResult.FAIL;
    var state = RestorationState.get(server);
    String kind = BuiltInRegistries.BLOCK.getKey(block).getPath();
    var m = block == Content.CORE ? state.reclaimedRegions.get(pos.asLong()) : null;
    if (m == null) m = state.machines.get(pos.asLong());
    if (m == null) {
      if (state.machines.size() + state.reclaimedRegions.size() >= 1024) {
        player.sendOverlayMessage(Component.literal("Restoration record limit reached."));
        return InteractionResult.FAIL;
      }
      m = new RestorationState.Machine(pos, kind);
      m.dirty = state::setDirty;
      m.biome = world.getBiome(pos).unwrapKey().orElseThrow().identifier().toString();
      m.family = BarrenWorld.family(m.biome);
      state.machines.put(pos.asLong(), m);
      state.setDirty();
    }
    MachineScreenHandler.open((ServerPlayer) player, m, false);
    if (m.kind.equals("restoration_core") && m.registered) m.caretakers.add(player.getUUID());
    if (block == Content.RECLAIMER && Multiblocks.issue(server, pos, kind) == 0)
      Journal.grant((ServerPlayer) player, "first_roots");
    return InteractionResult.SUCCESS;
  }

  public static void consume(Player p, ItemStack stack, int count) {
    if (!p.isCreative()) stack.shrink(count);
  }

  public static void give(Player p, ItemStack stack) {
    dev.reterra.compat.GameAccess.giveOrDrop(p, stack);
  }
}
