package dev.reterra.mixin;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobEntityMixin {
  @Inject(method = "finalizeSpawn", at = @At("RETURN"))
  private void reterra$markNaturalWildlife(
      ServerLevelAccessor world,
      DifficultyInstance difficulty,
      EntitySpawnReason reason,
      SpawnGroupData data,
      CallbackInfoReturnable<SpawnGroupData> cir) {
    Mob mob = (Mob) (Object) this;
    if (world.getLevel().dimension().equals(Level.OVERWORLD)
        && dev.reterra.world.WorldOptions.barren(world.getLevel())
        && (mob instanceof AgeableMob
            || mob instanceof AbstractVillager
            || (mob.getType().getCategory() != MobCategory.MONSTER
                && mob.getType().getCategory() != MobCategory.MISC))
        && (reason == EntitySpawnReason.NATURAL
            || reason == EntitySpawnReason.CHUNK_GENERATION
            || reason == EntitySpawnReason.STRUCTURE
            || reason == EntitySpawnReason.EVENT)) mob.addTag("reterra_barren_spawn");
  }
}
