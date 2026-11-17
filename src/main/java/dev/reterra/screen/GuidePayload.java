package dev.reterra.screen;

import dev.reterra.Content;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record GuidePayload(
    int chapter,
    int completed,
    String objective,
    String checklist,
    boolean open,
    net.minecraft.util.math.BlockPos farmhouse)
    implements CustomPayload {
  public static final Id<GuidePayload> ID = new Id<>(Content.id("field_guide"));
  public static final PacketCodec<RegistryByteBuf, GuidePayload> CODEC =
      new PacketCodec<>() {
        @Override
        public GuidePayload decode(RegistryByteBuf buf) {
          return new GuidePayload(
              buf.readVarInt(),
              buf.readVarInt(),
              buf.readString(1024),
              buf.readString(2048),
              buf.readBoolean(),
              buf.readBoolean() ? buf.readBlockPos() : null);
        }

        @Override
        public void encode(RegistryByteBuf buf, GuidePayload value) {
          buf.writeVarInt(value.chapter);
          buf.writeVarInt(value.completed);
          buf.writeString(value.objective, 1024);
          buf.writeString(value.checklist, 2048);
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
  public Id<? extends CustomPayload> getId() {
    return ID;
  }
}
