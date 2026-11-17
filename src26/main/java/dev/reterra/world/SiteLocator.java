package dev.reterra.world;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/** Searches real generated sites, one asynchronous chunk request at a time across the server. */
public final class SiteLocator {
  private SiteLocator() {}

  /** Register the locator ticket during mod initialization, before registries freeze. */
  public static void initialize() {}

  private static final ArrayDeque<Search> JOBS = new ArrayDeque<>();
  private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
  private static Search active;
  private static final net.minecraft.server.level.TicketType TICKET =
      net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE,
          dev.reterra.Content.id("locator"), new net.minecraft.server.level.TicketType(
              240, net.minecraft.server.level.TicketType.FLAG_LOADING));
  private static final int RADIUS = 2048;

  private static final class Search {
    final CommandSourceStack source;
    final ResearchSites.Kind kind;
    final ArrayDeque<BlockPos> candidates;
    CompletableFuture<?> pending;
    BlockPos checking;
    int requestedTick;
    boolean compass;

    Search(CommandSourceStack source, ResearchSites.Kind kind, List<BlockPos> candidates) {
      this.source = source;
      this.kind = kind;
      this.candidates = new ArrayDeque<>(candidates);
    }
  }

  public static List<BlockPos> candidates(long seed, BlockPos from, ResearchSites.Kind kind) {
    List<BlockPos> result = new ArrayList<>();
    int ox = Math.floorDiv(from.getX(), 128), oz = Math.floorDiv(from.getZ(), 128);
    for (int x = ox - 17; x <= ox + 17; x++)
      for (int z = oz - 17; z <= oz + 17; z++) {
        var actual = ResearchSites.kind(seed, x, z);
        if (actual != kind
            && !(kind == ResearchSites.Kind.FARMHOUSE && actual == ResearchSites.Kind.WATERWORKS))
          continue;
        BlockPos p = ResearchSites.candidate(seed, x, z);
        if (Math.abs((long) p.getX() - from.getX()) <= RADIUS
            && Math.abs((long) p.getZ() - from.getZ()) <= RADIUS) result.add(p);
      }
    result.sort(Comparator.comparingDouble(p -> horizontalDistance(p, from)));
    return result;
  }

  private static double horizontalDistance(BlockPos a, BlockPos b) {
    return Math.hypot((double) a.getX() - b.getX(), (double) a.getZ() - b.getZ());
  }

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        literal("locate").requires(s -> dev.reterra.compat.WorldAccess.gamemaster(s)).then(branch("reterra")));
    // Survival-friendly equivalent; vanilla /locate retains its usual operator permission.
    dispatcher.register(literal("reterra").then(branch("locate")));
    var structures = literal("structure");
    for (var kind : ResearchSites.Kind.values())
      structures.then(
          literal("reterra:" + kind.name().toLowerCase(Locale.ROOT))
              .executes(c -> start(c.getSource(), kind)));
    dispatcher.register(literal("locate").requires(s -> dev.reterra.compat.WorldAccess.gamemaster(s)).then(structures));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> branch(String name) {
    var branch =
        literal(name)
            .executes(
                c -> {
                  c.getSource()
                      .sendSuccess(
                          () ->
                              Component.literal(
                                  "Choose: farmhouse, greenhouse, seed_vault, waterworks,"
                                      + " biological_archive, research_station"),
                          false);
                  return 1;
                });
    for (var kind : ResearchSites.Kind.values())
      branch.then(
          literal(kind.name().toLowerCase(Locale.ROOT)).executes(c -> start(c.getSource(), kind)));
    return branch;
  }

  private static int start(CommandSourceStack source, ResearchSites.Kind kind) {
    return start(source, kind, false);
  }

  public static int followClue(
      net.minecraft.server.level.ServerPlayer player, ResearchSites.Kind kind) {
    return start(player.createCommandSourceStack(), kind, true);
  }

  private static int start(CommandSourceStack source, ResearchSites.Kind kind, boolean compass) {
    if (!source.getLevel().dimension().equals(Level.OVERWORLD)) {
      source.sendFailure(Component.literal("ReTerra ruins are found in the Overworld."));
      return 0;
    }
    var player = source.getPlayer();
    if (player != null) {
      long now = source.getServer().getTickCount();
      if (COOLDOWN.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) + 200 > now) {
        source.sendFailure(Component.literal("Please wait a moment before starting another search."));
        return 0;
      }
      if ((active != null && active.source.getPlayer() == player)
          || JOBS.stream().anyMatch(j -> j.source.getPlayer() == player)) {
        source.sendFailure(Component.literal("Your structure search is still running."));
        return 0;
      }
      COOLDOWN.put(player.getUUID(), now);
    }
    if (JOBS.size() >= 8) {
      source.sendFailure(Component.literal("Other explorers are searching. Try again shortly."));
      return 0;
    }
    BlockPos from = BlockPos.containing(source.getPosition());
    List<BlockPos> positions = candidates(source.getLevel().getSeed(), from, kind);
    dev.reterra.state.RestorationState.get(source.getLevel())
        .knownSites
        .forEach(
            (packed, name) -> {
              BlockPos pos = BlockPos.of(packed);
              if (name.equals(kind.name())
                  && Math.abs((long) pos.getX() - from.getX()) <= RADIUS
                  && Math.abs((long) pos.getZ() - from.getZ()) <= RADIUS
                  && positions.stream()
                      .noneMatch(p -> p.getX() == pos.getX() && p.getZ() == pos.getZ()))
                positions.add(pos);
            });
    positions.sort(Comparator.comparingDouble(p -> horizontalDistance(p, from)));
    if (compass && player != null) {
      var notes =
          dev.reterra.state.RestorationState.get(source.getLevel())
              .discoveries
              .getOrDefault(player.getUUID(), Set.of());
      positions.removeIf(
          p -> horizontalDistance(p, from) < 32 || notes.contains(SurveyClues.visitedKey(p)));
    }
    Search search = new Search(source, kind, positions);
    search.compass = compass;
    JOBS.add(search);
    if (compass && player != null)
      player.sendOverlayMessage(Component.literal("Tracing the old survey route..."));
    else
      source.sendSuccess(
          () -> Component.literal("Searching for " + label(kind) + " within 2,048 blocks..."), false);
    return 1;
  }

  public static String label(ResearchSites.Kind kind) {
    String[] words = kind.name().toLowerCase(Locale.ROOT).split("_");
    return String.join(
        " ",
        Arrays.stream(words)
            .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
            .toList());
  }

  private static void release(Search job) {
    if (job.checking != null) {
      var chunk = net.minecraft.world.level.ChunkPos.containing(job.checking);
      job.source.getLevel().getChunkSource().removeTicketWithRadius(TICKET, chunk, 0);
    }
  }

  public static void clear() {
    if (active != null) release(active);
    JOBS.clear();
    active = null;
    COOLDOWN.clear();
  }

  public static void tick(MinecraftServer server) {
    if (server.getTickCount() % 5 != 0) return;
    if (active == null) active = JOBS.poll();
    if (active == null) return;
    Search job = active;
    var player = job.source.getPlayer();
    if (player != null
        && (player.hasDisconnected() || dev.reterra.compat.WorldAccess.world(player) != job.source.getLevel())) {
      release(job);
      active = null;
      return;
    }
    ServerLevel world = job.source.getLevel();
    if (job.pending != null) {
      if (!job.pending.isDone()) {
        if (server.getTickCount() - job.requestedTick < 200) return;
        release(job);
        job.source.sendFailure(
            Component.literal(
                "Structure search timed out loading terrain. Try again after the server catches"
                    + " up."));
        active = null;
        return;
      }
      if (!job.pending.isCompletedExceptionally()) {
        var chunk =
            world
                .getChunkSource()
                .getChunk(
                    job.checking.getX() >> 4, job.checking.getZ() >> 4, ChunkStatus.FULL, false);
        if (chunk != null) {
          BlockPos found = findChest(world, job.checking, job.kind);
          if (found != null) {
            var state = dev.reterra.state.RestorationState.get(world);
            BlockPos offset = ResearchSites.chestPosition(BlockPos.ZERO, job.kind);
            BlockPos surface = found.subtract(offset);
            state.knownSites.put(surface.asLong(), job.kind.name());
            state.setDirty();
            if (job.compass && player != null) {
              SurveyClues.point(player, job.kind, found);
              release(job);
              active = null;
              return;
            }
            int distance =
                (int) horizontalDistance(found, BlockPos.containing(job.source.getPosition()));
            Component coordinates =
                Component.literal("[" + found.getX() + ", " + found.getY() + ", " + found.getZ() + "]")
                    .withStyle(ChatFormatting.GREEN)
                    .withStyle(
                        s ->
                            s.withClickEvent(
                                    dev.reterra.compat.TextEvents.suggest(
                                        "/tp @s "
                                            + found.getX()
                                            + " "
                                            + (found.getY() + 1)
                                            + " "
                                            + found.getZ()))
                                .withHoverEvent(
                                    dev.reterra.compat.TextEvents.hover(Component.literal("Suggest teleport command"))));
            job.source.sendSuccess(
                () ->
                    Component.literal(label(job.kind) + " found at ")
                        .append(coordinates)
                        .append(" (" + distance + " blocks away)."),
                false);
            release(job);
            active = null;
            return;
          }
        }
      }
      release(job);
      job.pending = null;
    }
    job.checking = job.candidates.poll();
    if (job.checking == null) {
      job.source.sendFailure(
          Component.literal(
              "No intact "
                  + label(job.kind)
                  + " found within 2,048 blocks. Explore farther and try again."));
      active = null;
      return;
    }
    if (!world.getWorldBorder().isWithinBounds(job.checking)) return;
    var chunk = net.minecraft.world.level.ChunkPos.containing(job.checking);
    world.getChunkSource().addTicketWithRadius(TICKET, chunk, 0);
    job.requestedTick = server.getTickCount();
    job.pending =
        world
            .getChunkSource()
            .getChunkFuture(
                job.checking.getX() >> 4, job.checking.getZ() >> 4, ChunkStatus.FULL, true);
  }

  public static BlockPos findChest(ServerLevel world, BlockPos candidate, ResearchSites.Kind kind) {
    BlockPos offset = ResearchSites.chestPosition(candidate, kind);
    int top = world.getHeight(Heightmap.Types.WORLD_SURFACE, offset.getX(), offset.getZ());
    for (int y = top; y >= Math.max(world.getMinY(), top - 32); y--) {
      BlockPos chest = new BlockPos(offset.getX(), y, offset.getZ());
      if (world.getBlockState(chest).is(Blocks.CHEST)) {
        // Archives and Waterworks share a chest offset. Verify their very different foundations.
        if (kind == ResearchSites.Kind.BIOLOGICAL_ARCHIVE
            && !world.getBlockState(chest.below()).is(Blocks.CALCITE)) continue;
        if (kind == ResearchSites.Kind.WATERWORKS
            && !world
                .getBlockState(new BlockPos(candidate.getX() + 3, y - 1, candidate.getZ()))
                .is(dev.reterra.compat.GameAccess.COPPER_BLOCK)) continue;
        return chest;
      }
    }
    return null;
  }
}
