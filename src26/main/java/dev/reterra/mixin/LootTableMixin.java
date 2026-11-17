package dev.reterra.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LootTable.class)
public abstract class LootTableMixin {
  @Inject(method = "fill", at = @At("TAIL"))
  private void reterra$recoverDifferentSpecimens(
      Container inventory, LootParams context, long seed, CallbackInfo ci) {
    dev.reterra.world.ArchiveSamples.diversify(context.getLevel(), inventory);
  }
}
