package dev.reterra;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class RestorationItem extends Item {
  private final String key;

  public RestorationItem(String key, Properties settings) {
    super(settings);
    this.key = key;
  }
  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context,
      net.minecraft.world.item.component.TooltipDisplay display,
      java.util.function.Consumer<Component> tooltip, TooltipFlag type) {
    appendHint(tooltip);
  }

  private void appendHint(java.util.function.Consumer<Component> tooltip) {
    String hint = Component.translatable("hint.reterra." + key).getString();
    StringBuilder line = new StringBuilder();
    for (String word : hint.split(" ")) {
      if (line.length() + word.length() > 48) {
        tooltip.accept(Component.literal(line.toString()).withStyle(ChatFormatting.GRAY));
        line.setLength(0);
      }
      if (!line.isEmpty()) line.append(' ');
      line.append(word);
    }
    if (!line.isEmpty()) tooltip.accept(Component.literal(line.toString()).withStyle(ChatFormatting.GRAY));
  }
}
