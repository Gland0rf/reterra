package dev.reterra.effect;

import dev.reterra.Content;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Short visual cues; clients animate locally without adding entities to the world. */
public record AnimationPayload(BlockPos origin, BlockPos target, Kind kind, float progress)
    implements CustomPacketPayload {
  public enum Kind {
    RECLAIMER,
    CORE,
    WATER,
    REVIVAL,
    REVIVED,
    HEART,
    HEART_FINISH,
    HEART_STOP,
    GROWTH,
    MILESTONE
  }

  public static final Type<AnimationPayload> ID = new Type<>(Content.id("animation"));
  public static final StreamCodec<RegistryFriendlyByteBuf, AnimationPayload> CODEC =
      new StreamCodec<>() {
        @Override
        public AnimationPayload decode(RegistryFriendlyByteBuf buf) {
          return new AnimationPayload(
              buf.readBlockPos(),
              buf.readBlockPos(),
              buf.readEnum(Kind.class),
              buf.readFloat());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, AnimationPayload value) {
          buf.writeBlockPos(value.origin);
          buf.writeBlockPos(value.target);
          buf.writeEnum(value.kind);
          buf.writeFloat(value.progress);
        }
      };

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return ID;
  }
}
