package dev.reterra.machine;

import dev.reterra.Content;
import dev.reterra.Journal;
import dev.reterra.screen.MachineScreenHandler;
import dev.reterra.state.RestorationState;
import dev.reterra.world.BarrenWorld;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class MachineInteraction {
  private MachineInteraction() {}

  public static ActionResult use(
      PlayerEntity player, World world, Hand hand, net.minecraft.util.hit.BlockHitResult hit) {
    if (player.isSpectator()) return ActionResult.PASS;
    BlockPos pos = hit.getBlockPos();
    ItemStack held = player.getStackInHand(hand);
    if (held.isOf(Content.SCANNER)) {
      if (player instanceof ServerPlayerEntity server) {
        var core = RestorationEngine.regionAt(RestorationState.get(dev.reterra.compat.WorldAccess.world(server)), pos);
        if (core == null) MachineScreenHandler.preview(server, pos.up());
        else if (player.isSneaking()) dev.reterra.ReTerra.inspect(server);
        else MachineScreenHandler.open(server, core, true);
      }
      return ActionResult.SUCCESS;
    }
    if (held.isOf(Content.COMPOST)
        && world.getBlockState(pos).getBlock() instanceof net.minecraft.block.CropBlock crop) {
      if (!dev.reterra.compat.WorldAccess.canModify(world, player, pos) || !player.canModifyBlocks())
        return ActionResult.FAIL;
      var planted = world.getBlockState(pos);
      if (crop.isMature(planted)) return ActionResult.PASS;
      if (world instanceof ServerWorld server) {
        world.setBlockState(
            pos, crop.withAge(Math.min(crop.getMaxAge(), crop.getAge(planted) + 2)));
        consume(player, held, 1);
        Stewardship.credit(server, pos, "tend");
        dev.reterra.effect.AnimationSignals.emit(
            server, pos, pos, dev.reterra.effect.AnimationPayload.Kind.GROWTH, 0);
      }
      return ActionResult.SUCCESS;
    }
    if ((held.isOf(Content.COMPOST) || held.isOf(Items.BONE_MEAL))
        && world.getBlockState(pos).isOf(Blocks.GRASS_BLOCK)) {
      if (!dev.reterra.compat.WorldAccess.canModify(world, player, pos) || !player.canModifyBlocks())
        return ActionResult.FAIL;
      if (world instanceof ServerWorld server) {
        int grown = 0;
        for (int dx = -2; dx <= 2 && grown < 5; dx++)
          for (int dz = -2; dz <= 2 && grown < 5; dz++) {
            BlockPos flower = pos.add(dx, 1, dz);
            if (!RestorationEngine.loaded(server, flower)
                || !dev.reterra.compat.WorldAccess.canModify(world, player, flower)
                || !world.getBlockState(flower.down()).isOf(Blocks.GRASS_BLOCK)
                || !world.getBlockState(flower).isAir()) continue;
            var core = RestorationEngine.regionAt(RestorationState.get(server), pos);
            String biome =
                core == null
                    ? world.getBiome(pos).getKey().orElseThrow().getValue().toString()
                    : core.biome;
            var palette =
                dev.reterra.ecology.HabitatPlants.flowers(
                    core == null ? BarrenWorld.family(biome) : core.family,
                    dev.reterra.ecology.HabitatPlants.variant(biome));
            var plant = palette.get(grown % palette.size()).getDefaultState();
            if (!plant.canPlaceAt(world, flower)) continue;
            world.setBlockState(flower, plant);
            grown++;
            Stewardship.plant(server, flower);
          }
        if (grown > 0) {
          consume(player, held, 1);
          dev.reterra.effect.AnimationSignals.emit(
              server, pos, pos, dev.reterra.effect.AnimationPayload.Kind.GROWTH, 1);
          return ActionResult.SUCCESS;
        }
      } else return ActionResult.SUCCESS;
    }
    boolean culture = held.isOf(Content.CULTURE) && world.getBlockState(pos).isOf(Blocks.DIRT);
    boolean collect =
        held.isOf(Items.GLASS_BOTTLE) && world.getBlockState(pos).isOf(Blocks.GRASS_BLOCK);
    if (culture || collect) {
      if (!dev.reterra.compat.WorldAccess.canModify(world, player, pos) || !player.canModifyBlocks())
        return ActionResult.FAIL;
      if (player instanceof ServerPlayerEntity server) {
        if (culture) {
          int grown = 0;
          for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
              BlockPos seed = pos.add(dx, 0, dz);
              if (RestorationEngine.loaded(dev.reterra.compat.WorldAccess.world(server), seed)
                  && dev.reterra.compat.WorldAccess.canModify(world, player, seed)
                  && world.getBlockState(seed).isOf(Blocks.DIRT)
                  && !dev.reterra.compat.WorldAccess.opaque(world, seed.up())
                  && world.getFluidState(seed.up()).isEmpty()) {
                world.setBlockState(seed, Blocks.GRASS_BLOCK.getDefaultState());
                grown++;
              }
            }
          if (grown == 0) return ActionResult.FAIL;
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
      return ActionResult.SUCCESS;
    }
    var block = world.getBlockState(pos).getBlock();
    if (!Content.isMachine(block)) return ActionResult.PASS;
    if (player.isSneaking() && held.getItem() instanceof BlockItem) return ActionResult.PASS;
    if (!dev.reterra.compat.WorldAccess.canModify(world, player, pos) || !player.canModifyBlocks())
      return ActionResult.FAIL;
    if (world.isClient()) return ActionResult.SUCCESS;
    if (!(world instanceof ServerWorld server) || !world.getRegistryKey().equals(World.OVERWORLD))
      return ActionResult.FAIL;
    var state = RestorationState.get(server);
    String kind = Registries.BLOCK.getId(block).getPath();
    var m = block == Content.CORE ? state.reclaimedRegions.get(pos.asLong()) : null;
    if (m == null) m = state.machines.get(pos.asLong());
    if (m == null) {
      if (state.machines.size() + state.reclaimedRegions.size() >= 1024) {
        player.sendMessage(Text.literal("Restoration record limit reached."), true);
        return ActionResult.FAIL;
      }
      m = new RestorationState.Machine(pos, kind);
      m.dirty = state::markDirty;
      m.biome = world.getBiome(pos).getKey().orElseThrow().getValue().toString();
      m.family = BarrenWorld.family(m.biome);
      state.machines.put(pos.asLong(), m);
      state.markDirty();
    }
    MachineScreenHandler.open((ServerPlayerEntity) player, m, false);
    if (m.kind.equals("restoration_core") && m.registered) m.caretakers.add(player.getUuid());
    if (block == Content.RECLAIMER && Multiblocks.issue(server, pos, kind) == 0)
      Journal.grant((ServerPlayerEntity) player, "first_roots");
    return ActionResult.SUCCESS;
  }

  public static void consume(PlayerEntity p, ItemStack stack, int count) {
    if (!p.isCreative()) stack.decrement(count);
  }

  public static void give(PlayerEntity p, ItemStack stack) {
    if (!p.getInventory().insertStack(stack)) p.dropItem(stack, false);
  }
}
