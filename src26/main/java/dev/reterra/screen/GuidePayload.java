package dev.reterra.screen;

import dev.reterra.Content;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GuidePayload(
    int chapter,
    int completed,
    String objective,
    String checklist,
    boolean open,
    net.minecraft.core.BlockPos farmhouse)
    implements CustomPacketPayload {
  public static final Type<GuidePayload> ID = new Type<>(Content.id("field_guide"));
  public static final StreamCodec<RegistryFriendlyByteBuf, GuidePayload> CODEC =
      new StreamCodec<>() {
        @Override
        public GuidePayload decode(RegistryFriendlyByteBuf buf) {
          return new GuidePayload(
              buf.readVarInt(),
              buf.readVarInt(),
              buf.readUtf(1024),
              buf.readUtf(2048),
              buf.readBoolean(),
              buf.readBoolean() ? buf.readBlockPos() : null);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, GuidePayload value) {
          buf.writeVarInt(value.chapter);
          buf.writeVarInt(value.completed);
          buf.writeUtf(value.objective, 1024);
          buf.writeUtf(value.checklist, 2048);
          buf.writeBoolean(value.open);
          buf.writeBoolean(value.farmhouse != null);
          if (value.farmhouse != null) buf.writeBlockPos(value.farmhouse);
        }
      };

  public GuidePayload(
      int chapter, int completed, String objective, String checklist, boolean open) {
    this(chapter, completed, objective, checklist, open, null);
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return ID;
  }
}
