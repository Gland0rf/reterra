package dev.reterra.world;

import dev.reterra.Content;
import dev.reterra.Journal;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

public final class SurveyClues {
  private SurveyClues() {}

  public static ResearchSites.Kind destination(ItemStack notes, int chapter) {
    String clue =
        dev.reterra.compat.NbtAccess.string(notes
            .getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
            .copyNbt(), "reterra_clue");
    try {
      return ResearchSites.Kind.valueOf(clue.toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return chapter >= 3 ? ResearchSites.Kind.BIOLOGICAL_ARCHIVE : ResearchSites.Kind.GREENHOUSE;
    }
  }

  public static boolean follow(ServerPlayerEntity player, Hand notesHand) {
    Hand compassHand = notesHand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
    if (!player.getStackInHand(compassHand).isOf(Items.COMPASS)) return false;
    //? if <1.21.2 {
    if (player.getItemCooldownManager().isCoolingDown(Content.FIELD_JOURNAL)) return true;
    player.getItemCooldownManager().set(Content.FIELD_JOURNAL, 200);
    //? } else {
    /*ItemStack notes = player.getStackInHand(notesHand);
    if (player.getItemCooldownManager().isCoolingDown(notes)) return true;
    player.getItemCooldownManager().set(notes, 200);
    *///? }
    SiteLocator.followClue(
        player, destination(player.getStackInHand(notesHand), Journal.payload(player).chapter()));
    return true;
  }

  public static boolean point(
      ServerPlayerEntity player, ResearchSites.Kind kind, BlockPos destination) {
    for (Hand hand : Hand.values())
      if (player.getStackInHand(hand).isOf(Items.COMPASS)) {
        Hand other = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
        if (!player.getStackInHand(other).isOf(Content.FIELD_JOURNAL)) continue;
        ItemStack compass = player.getStackInHand(hand);
        compass.set(
            DataComponentTypes.LODESTONE_TRACKER,
            new LodestoneTrackerComponent(
                java.util.Optional.of(GlobalPos.create(World.OVERWORLD, destination)), false));
        compass.set(
            DataComponentTypes.CUSTOM_NAME, Text.literal("Survey: " + SiteLocator.label(kind)));
        player.sendMessage(Text.literal("Survey compass set: " + SiteLocator.label(kind)), true);
        return true;
      }
    player.sendMessage(
        Text.literal("Keep the notes and compass in your hands to follow the survey."), true);
    return false;
  }

  public static String visitedKey(BlockPos surface) {
    return "visited_" + surface.getX() + "_" + surface.getZ();
  }
}
