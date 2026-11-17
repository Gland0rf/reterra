package dev.reterra.effect;

import dev.reterra.Content;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** Short visual cues; clients animate locally without adding entities to the world. */
public record AnimationPayload(BlockPos origin, BlockPos target, Kind kind, float progress)
    implements CustomPayload {
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

  public static final Id<AnimationPayload> ID = new Id<>(Content.id("animation"));
  public static final PacketCodec<RegistryByteBuf, AnimationPayload> CODEC =
      new PacketCodec<>() {
        @Override
        public AnimationPayload decode(RegistryByteBuf buf) {
          return new AnimationPayload(
              buf.readBlockPos(),
              buf.readBlockPos(),
              buf.readEnumConstant(Kind.class),
              buf.readFloat());
        }

        @Override
        public void encode(RegistryByteBuf buf, AnimationPayload value) {
          buf.writeBlockPos(value.origin);
          buf.writeBlockPos(value.target);
          buf.writeEnumConstant(value.kind);
          buf.writeFloat(value.progress);
        }
      };

  @Override
  public Id<? extends CustomPayload> getId() {
    return ID;
  }
}
