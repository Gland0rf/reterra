package dev.reterra.mixin;

import net.minecraft.inventory.Inventory;
import net.minecraft.loot.LootTable;
//? if <1.21.2 {
import net.minecraft.loot.context.LootContextParameterSet;
//? } else {
/*import net.minecraft.loot.context.LootWorldContext;
*///? }
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LootTable.class)
public abstract class LootTableMixin {
  @Inject(method = "supplyInventory", at = @At("TAIL"))
  private void reterra$recoverDifferentSpecimens(
      //? if <1.21.2 {
      Inventory inventory, LootContextParameterSet context, long seed, CallbackInfo ci) {
      //? } else {
      /*Inventory inventory, LootWorldContext context, long seed, CallbackInfo ci) {
      *///? }
    dev.reterra.world.ArchiveSamples.diversify(context.getWorld(), inventory);
  }
}
