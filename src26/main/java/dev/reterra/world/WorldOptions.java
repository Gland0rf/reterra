package dev.reterra.world;
import net.minecraft.world.level.Level;

/** Per-save world-generation option, also exposed in the new-world Game Rules menu. */
public final class WorldOptions {
  public static final net.minecraft.world.level.gamerules.GameRule<Boolean> BARREN =
      net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder.forBoolean(true)
          .category(net.minecraft.world.level.gamerules.GameRuleCategory.UPDATES)
          .buildAndRegister(dev.reterra.Content.id("barren_world"));

  private WorldOptions() {}

  public static boolean barren(Level world) {
    if (!(world instanceof net.minecraft.server.level.ServerLevel server)) return false;
    return server.getGameRules().get(BARREN);
  }

  public static void setBarren(net.minecraft.server.level.ServerLevel world, boolean enabled) {
    world.getGameRules().set(BARREN, enabled, world.getServer());
  }

  public static void initialize() {}
}
