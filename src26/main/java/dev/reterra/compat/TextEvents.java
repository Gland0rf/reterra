package dev.reterra.compat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public final class TextEvents {
  private TextEvents() {}

  public static ClickEvent run(String command) {
    return new ClickEvent.RunCommand(command);
  }

  public static ClickEvent suggest(String command) {
    return new ClickEvent.SuggestCommand(command);
  }

  public static HoverEvent hover(Component text) {
    return new HoverEvent.ShowText(text);
  }
}
