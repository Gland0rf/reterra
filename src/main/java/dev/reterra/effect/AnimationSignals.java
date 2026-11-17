package dev.reterra.effect;

import dev.reterra.state.RestorationState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Coalesce work into at most one cue per source/kind per tick; never save visual events. */
public final class AnimationSignals {
  public static final int MAX_QUEUED = 128;

  public record Key(BlockPos pos, AnimationPayload.Kind kind) {}

  private AnimationSignals() {}

  public static void emit(
      ServerWorld world,
      BlockPos origin,
      BlockPos target,
      AnimationPayload.Kind kind,
      float progress) {
    var pending = RestorationState.get(world).visualSignals;
    var key = new Key(origin.toImmutable(), kind);
    if (pending.size() >= MAX_QUEUED && !pending.containsKey(key)) {
      if (kind != AnimationPayload.Kind.HEART
          && kind != AnimationPayload.Kind.HEART_FINISH
          && kind != AnimationPayload.Kind.HEART_STOP) return;
      pending.remove(pending.keySet().iterator().next());
    }
    pending.put(
        key, new AnimationPayload(origin.toImmutable(), target.toImmutable(), kind, progress));
  }

  public static void stopHeart(ServerWorld world, BlockPos origin) {
    if (origin == null) return;
    var pending = RestorationState.get(world).visualSignals;
    pending
        .keySet()
        .removeIf(
            key ->
                key.pos.equals(origin)
                    && (key.kind == AnimationPayload.Kind.HEART
                        || key.kind == AnimationPayload.Kind.HEART_FINISH));
    emit(world, origin, origin, AnimationPayload.Kind.HEART_STOP, 0);
  }

  public static void flush(ServerWorld world) {
    var pending = RestorationState.get(world).visualSignals;
    if (pending.isEmpty()) return;
    for (var cue : pending.values())
      for (var player : world.getPlayers())
        if (player.squaredDistanceTo(cue.origin().toCenterPos()) <= 64 * 64
            && ServerPlayNetworking.canSend(player, AnimationPayload.ID))
          ServerPlayNetworking.send(player, cue);
    pending.clear();
  }
}
