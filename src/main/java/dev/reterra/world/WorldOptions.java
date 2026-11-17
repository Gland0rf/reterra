package dev.reterra.world;

//? if <1.21.11 {
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;
//? }
import net.minecraft.world.World;

/** Per-save world-generation option, also exposed in the new-world Game Rules menu. */
public final class WorldOptions {
  //? if <1.21.11 {
  public static final GameRules.Key<GameRules.BooleanRule> BARREN =
      GameRuleRegistry.register(
          "reterraBarrenWorld",
          GameRules.Category.UPDATES,
          GameRuleFactory.createBooleanRule(true));
  //? } else {
  /*public static final net.minecraft.world.rule.GameRule<Boolean> BARREN =
      net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder.forBoolean(true)
          .category(net.minecraft.world.rule.GameRuleCategory.UPDATES)
          .buildAndRegister(dev.reterra.Content.id("barren_world"));
  *///? }

  private WorldOptions() {}

  public static boolean barren(World world) {
    if (!(world instanceof net.minecraft.server.world.ServerWorld server)) return false;
    //? if <1.21.11 {
    return server.getGameRules().getBoolean(BARREN);
    //? } else {
    /*return server.getGameRules().getValue(BARREN);
    *///? }
  }

  public static void setBarren(net.minecraft.server.world.ServerWorld world, boolean enabled) {
    //? if <1.21.11 {
    world.getGameRules().get(BARREN).set(enabled, world.getServer());
    //? } else {
    /*world.getGameRules().setValue(BARREN, enabled, world.getServer());
    *///? }
  }

  public static void initialize() {}
}
