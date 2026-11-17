package dev.reterra.mixin;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import net.minecraft.util.datafix.fixes.GameRuleRegistryFix;
import net.minecraft.util.datafix.fixes.References;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRuleRegistryFix.class)
public abstract class GameRuleMigrationMixin extends DataFix {
  protected GameRuleMigrationMixin(Schema schema, boolean changesType) {
    super(schema, changesType);
  }

  @Inject(method = "makeRule", at = @At("RETURN"), cancellable = true)
  private void reterra$keepTerrainChoice(CallbackInfoReturnable<TypeRewriteRule> result) {
    // Preserve normal-world saves when vanilla moves game rules into a registry.
    var migrate = fixTypeEverywhereTyped("ReTerra terrain option",
        getInputSchema().getType(References.LEVEL),
        typed -> typed.update(DSL.remainderFinder(), data -> data.update("GameRules", rules ->
            rules.renameAndFixField("reterraBarrenWorld", "reterra:barren_world",
                value -> value.createBoolean(Boolean.parseBoolean(value.asString("true")))))));
    result.setReturnValue(TypeRewriteRule.seq(migrate, result.getReturnValue()));
  }
}
