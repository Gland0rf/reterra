package dev.reterra.client;

import java.util.Optional;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

/** An ordinary animated compass model, displayed until the first seeds are recovered. */
public final class FarmhouseCompass {
  private static BlockPos target;
  private static ItemStack compass = ItemStack.EMPTY;

  private FarmhouseCompass() {}

  public static void update(BlockPos position) {
    if (java.util.Objects.equals(target, position)) return;
    target = position;
    compass = position == null ? ItemStack.EMPTY : new ItemStack(Items.COMPASS);
    if (position != null)
      compass.set(
          DataComponents.LODESTONE_TRACKER,
          new LodestoneTracker(
              Optional.of(GlobalPos.of(Level.OVERWORLD, position)), false));
  }

  public static void extractRenderState(GuiGraphicsExtractor c, DeltaTracker ticks) {
    Minecraft client = Minecraft.getInstance();
    if (target == null
        || client.player == null
        || client.level == null
        || dev.reterra.client.ClientAccess.hidden(client)
        || dev.reterra.client.ClientAccess.screen(client) != null
        || !client.level.dimension().equals(Level.OVERWORLD)) return;
    int left = c.guiWidth() / 2 - 55;
    c.fill(left, 7, left + 110, 45, 0xBB18251D);
    FieldUi.push(c, left + 4, 10, 2f);
    c.item(compass, 0, 0);
    FieldUi.pop(c);
    c.text(client.font, "Farmhouse", left + 40, 22, 0xFFE8E3CA, false);
  }
}
