package dev.reterra.world;

import dev.reterra.Content;
import dev.reterra.Journal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

public final class SurveyClues {
  private SurveyClues() {}

  public static ResearchSites.Kind destination(ItemStack notes, int chapter) {
    String clue =
        dev.reterra.compat.NbtAccess.string(notes
            .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag(), "reterra_clue");
    try {
      return ResearchSites.Kind.valueOf(clue.toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return chapter >= 3 ? ResearchSites.Kind.BIOLOGICAL_ARCHIVE : ResearchSites.Kind.GREENHOUSE;
    }
  }

  public static boolean follow(ServerPlayer player, InteractionHand notesHand) {
    InteractionHand compassHand = notesHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    if (!player.getItemInHand(compassHand).is(Items.COMPASS)) return false;
    ItemStack notes = player.getItemInHand(notesHand);
    if (player.getCooldowns().isOnCooldown(notes)) return true;
    player.getCooldowns().addCooldown(notes, 200);
    SiteLocator.followClue(
        player, destination(player.getItemInHand(notesHand), Journal.payload(player).chapter()));
    return true;
  }

  public static boolean point(
      ServerPlayer player, ResearchSites.Kind kind, BlockPos destination) {
    for (InteractionHand hand : InteractionHand.values())
      if (player.getItemInHand(hand).is(Items.COMPASS)) {
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!player.getItemInHand(other).is(Content.FIELD_JOURNAL)) continue;
        ItemStack compass = player.getItemInHand(hand);
        compass.set(
            DataComponents.LODESTONE_TRACKER,
            new LodestoneTracker(
                java.util.Optional.of(GlobalPos.of(Level.OVERWORLD, destination)), false));
        compass.set(
            DataComponents.CUSTOM_NAME, Component.literal("Survey: " + SiteLocator.label(kind)));
        player.sendOverlayMessage(Component.literal("Survey compass set: " + SiteLocator.label(kind)));
        return true;
      }
    player.sendOverlayMessage(
        Component.literal("Keep the notes and compass in your hands to follow the survey."));
    return false;
  }

  public static String visitedKey(BlockPos surface) {
    return "visited_" + surface.getX() + "_" + surface.getZ();
  }
}
