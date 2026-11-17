package dev.reterra.mixin;

import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnimalEntity.class)
public abstract class AnimalCareMixin {
  @Unique private boolean reterra$wasReady;

  @Inject(method = "interactMob", at = @At("HEAD"))
  private void reterra$beforeFeeding(
      PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> ci) {
    AnimalEntity animal = (AnimalEntity) (Object) this;
    reterra$wasReady =
        !animal.isBaby()
            && !animal.isInLove()
            && animal.isBreedingItem(player.getStackInHand(hand));
  }

  @Inject(method = "interactMob", at = @At("RETURN"))
  private void reterra$afterFeeding(
      PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> ci) {
    AnimalEntity animal = (AnimalEntity) (Object) this;
    if (reterra$wasReady
        && ci.getReturnValue().isAccepted()
        && animal.isInLove()
        && dev.reterra.compat.WorldAccess.entityWorld(animal) instanceof ServerWorld world)
      dev.reterra.machine.Stewardship.credit(world, animal.getBlockPos(), "feed");
  }
}
