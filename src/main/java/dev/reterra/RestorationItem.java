package dev.reterra;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class RestorationItem extends Item {
  private final String key;

  public RestorationItem(String key, Settings settings) {
    super(settings);
    this.key = key;
  }

  //? if <1.21.5 {
  @Override
  public void appendTooltip(
      ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
    appendHint(tooltip::add);
  }
  //? } else {
  /*@Override
  public void appendTooltip(ItemStack stack, TooltipContext context,
      net.minecraft.component.type.TooltipDisplayComponent display,
      java.util.function.Consumer<Text> tooltip, TooltipType type) {
    appendHint(tooltip);
  }
  *///? }

  private void appendHint(java.util.function.Consumer<Text> tooltip) {
    String hint = Text.translatable("hint.reterra." + key).getString();
    StringBuilder line = new StringBuilder();
    for (String word : hint.split(" ")) {
      if (line.length() + word.length() > 48) {
        tooltip.accept(Text.literal(line.toString()).formatted(Formatting.GRAY));
        line.setLength(0);
      }
      if (!line.isEmpty()) line.append(' ');
      line.append(word);
    }
    if (!line.isEmpty()) tooltip.accept(Text.literal(line.toString()).formatted(Formatting.GRAY));
  }
}
