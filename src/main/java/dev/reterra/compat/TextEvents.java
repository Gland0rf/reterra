package dev.reterra.compat;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

public final class TextEvents {
  private TextEvents() {}

  public static ClickEvent run(String command) {
    //? if <1.21.5 {
    return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
    //? } else {
    /*return new ClickEvent.RunCommand(command);
    *///? }
  }

  public static ClickEvent suggest(String command) {
    //? if <1.21.5 {
    return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);
    //? } else {
    /*return new ClickEvent.SuggestCommand(command);
    *///? }
  }

  public static HoverEvent hover(Text text) {
    //? if <1.21.5 {
    return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
    //? } else {
    /*return new HoverEvent.ShowText(text);
    *///? }
  }
}
