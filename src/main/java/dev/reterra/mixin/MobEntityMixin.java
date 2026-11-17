package dev.reterra.mixin;

import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
  @Inject(method = "initialize", at = @At("RETURN"))
  private void reterra$markNaturalWildlife(
      ServerWorldAccess world,
      LocalDifficulty difficulty,
      SpawnReason reason,
      EntityData data,
      CallbackInfoReturnable<EntityData> cir) {
    MobEntity mob = (MobEntity) (Object) this;
    if (world.toServerWorld().getRegistryKey().equals(World.OVERWORLD)
        && dev.reterra.world.WorldOptions.barren(world.toServerWorld())
        && (mob instanceof PassiveEntity
            || mob instanceof MerchantEntity
            || (mob.getType().getSpawnGroup() != SpawnGroup.MONSTER
                && mob.getType().getSpawnGroup() != SpawnGroup.MISC))
        && (reason == SpawnReason.NATURAL
            || reason == SpawnReason.CHUNK_GENERATION
            || reason == SpawnReason.STRUCTURE
            || reason == SpawnReason.EVENT)) mob.addCommandTag("reterra_barren_spawn");
  }
}
