package dev.reterra.world;

import dev.reterra.compat.NbtAccess;
import dev.reterra.machine.RestorationEngine;
import dev.reterra.state.RestorationState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Small resumable canopy jobs follow existing branches and preserve the original wood species. */
public final class TreeRecovery {
  public static final int MAX_JOBS = 128;

  public static final class Job {
    public final BlockPos base, owner;
    public final boolean heart;
    public int cursor;
    private List<BlockPos> leaves;
    private Block leafBlock;

    public Job(BlockPos base, BlockPos owner, boolean heart) {
      this.base = base.toImmutable();
      this.owner = owner.toImmutable();
      this.heart = heart;
    }

    public NbtCompound write() {
      NbtCompound n = new NbtCompound();
      n.putLong("base", base.asLong());
      n.putLong("owner", owner.asLong());
      n.putBoolean("heart", heart);
      n.putInt("cursor", cursor);
      return n;
    }

    public static Job read(NbtCompound n) {
      Job job =
          new Job(
              BlockPos.fromLong(NbtAccess.longValue(n, "base")),
              BlockPos.fromLong(NbtAccess.longValue(n, "owner")),
              NbtAccess.bool(n, "heart"));
      job.cursor = Math.max(0, NbtAccess.integer(n, "cursor"));
      return job;
    }
  }

  private TreeRecovery() {}

  public static void enqueueColumn(
      ServerWorld world, RestorationState state, RestorationState.Machine region, BlockPos base) {
    boolean roots = false;
    for (int y = 0; y < 10; y++) {
      BlockPos p = base.up(y);
      var block = world.getBlockState(p);
      roots |= block.isOf(Blocks.MANGROVE_ROOTS) || block.isOf(Blocks.MUDDY_MANGROVE_ROOTS);
      if (block.isAir() && !roots) return;
      if (block.isIn(BlockTags.LOGS)) {
        enqueue(world, state, region, p);
        return;
      }
      if (!block.isAir()
          && !block.isOf(Blocks.WATER)
          && !block.isOf(Blocks.MANGROVE_ROOTS)
          && !block.isOf(Blocks.MUDDY_MANGROVE_ROOTS)
          && !(block.getBlock() instanceof net.minecraft.block.PlantBlock)) return;
    }
  }

  public static void enqueue(
      ServerWorld world, RestorationState state, RestorationState.Machine region, BlockPos base) {
    if (state.treePlansRemaining <= 0
        || state.treeJobs.size() >= MAX_JOBS
        || state.treeJobs.containsKey(base.asLong())
        || state.protectedBlocks.contains(base.asLong())
        || !world.getBlockState(base).isIn(BlockTags.LOGS)) return;
    // A branch column must not be mistaken for another tree base.
    if (world.getBlockState(base.down()).isIn(BlockTags.LOGS)) return;
    state.treePlansRemaining--;
    Job job = new Job(base, region.pos, region.kind.equals("heart_of_the_world"));
    if (plan(world, state, job)
        && job.leaves.stream().anyMatch(p -> world.getBlockState(p).isAir()))
      state.treeJobs.put(base.asLong(), job);
  }

  private static boolean plan(ServerWorld world, RestorationState state, Job job) {
    Block trunk = world.getBlockState(job.base).getBlock();
    if (!world.getBlockState(job.base).isIn(BlockTags.LOGS)) return false;
    String name = Registries.BLOCK.getId(trunk).getPath();
    job.leafBlock =
        Registries.BLOCK.get(
            net.minecraft.util.Identifier.ofVanilla(
                name.replace("stripped_", "")
                    .replace("_log", "_leaves")
                    .replace("_wood", "_leaves")));
    if (!(job.leafBlock instanceof LeavesBlock)) return false;
    var queue = new ArrayDeque<BlockPos>();
    var wood = new LinkedHashSet<BlockPos>();
    var seen = new HashSet<BlockPos>();
    queue.add(job.base);
    seen.add(job.base);
    while (!queue.isEmpty() && wood.size() < 256) {
      BlockPos p = queue.removeFirst();
      if (state.protectedBlocks.contains(p.asLong())) return false;
      wood.add(p);
      for (int dx = -1; dx <= 1; dx++)
        for (int dy = -1; dy <= 1; dy++)
          for (int dz = -1; dz <= 1; dz++) {
            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 2 || dx == 0 && dy == 0 && dz == 0)
              continue;
            BlockPos next = p.add(dx, dy, dz);
            if (next.getY() < job.base.getY()
                || next.getY() > job.base.getY() + 48
                || Math.abs(next.getX() - job.base.getX()) > 10
                || Math.abs(next.getZ() - job.base.getZ()) > 10
                || !RestorationEngine.loaded(world, next)
                || !seen.add(next)) continue;
            if (world.getBlockState(next).isOf(trunk)) queue.addLast(next);
          }
    }
    int top = wood.stream().mapToInt(BlockPos::getY).max().orElse(job.base.getY());
    if (top - job.base.getY() < 2) return false;
    var candidates = new LinkedHashSet<BlockPos>();
    boolean spruce = name.contains("spruce"), cherry = name.contains("cherry");
    for (BlockPos log : wood) {
      boolean tip = !wood.contains(log.up()) && log.getY() >= job.base.getY() + 2;
      if (spruce)
        tip =
            log.getY() >= job.base.getY() + (top - job.base.getY()) / 3
                && (top - log.getY()) % 2 == 0;
      if (!tip) continue;
      int radius = spruce ? Math.min(3, 1 + (top - log.getY()) / 3) : cherry ? 3 : 2;
      for (int dx = -radius; dx <= radius; dx++)
        for (int dz = -radius; dz <= radius; dz++)
          for (int dy = -1; dy <= 2; dy++) {
            if (dx * dx + dz * dz + dy * dy * 2 > radius * radius + 1) continue;
            BlockPos leaf = log.add(dx, dy, dz);
            if (RestorationEngine.loaded(world, leaf) && !wood.contains(leaf)) candidates.add(leaf);
          }
    }
    job.leaves = new ArrayList<>(candidates);
    job.leaves.sort(Comparator.comparingInt(BlockPos::getY).thenComparingLong(BlockPos::asLong));
    return !job.leaves.isEmpty();
  }

  public static void tick(ServerWorld world, RestorationState state) {
    int writes = 0, jobs = 0;
    var iterator = state.treeJobs.entrySet().iterator();
    while (iterator.hasNext() && writes < 24 && jobs < 4) {
      Job job = iterator.next().getValue();
      if (job.heart
          ? !state.heartActivated
          : !state.reclaimedRegions.containsKey(job.owner.asLong())) {
        iterator.remove();
        continue;
      }
      if (!RestorationEngine.loaded(world, job.base)) continue;
      jobs++;
      if (!world.getBlockState(job.base).isIn(BlockTags.LOGS)
          || state.protectedBlocks.contains(job.base.asLong())) {
        iterator.remove();
        continue;
      }
      if (job.leaves == null && !plan(world, state, job)) {
        iterator.remove();
        continue;
      }
      int scanned = 0;
      while (job.cursor < job.leaves.size() && writes < 24 && scanned++ < 128) {
        BlockPos p = job.leaves.get(job.cursor);
        if (!RestorationEngine.loaded(world, p)) break;
        job.cursor++;
        var owner = RestorationEngine.regionAt(state, p);
        if (owner != null && (job.heart || !owner.pos.equals(job.owner))
            || state.protectedBlocks.contains(p.asLong())
            || !world.getBlockState(p).isAir()) continue;
        world.setBlockState(
            p, job.leafBlock.getDefaultState().with(LeavesBlock.DISTANCE, 1), Block.NOTIFY_ALL);
        writes++;
      }
      if (job.cursor >= job.leaves.size()) iterator.remove();
    }
    if (jobs > 0) state.markDirty();
  }
}
