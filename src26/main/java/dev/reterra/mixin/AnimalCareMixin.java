package dev.reterra.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Animal.class)
public abstract class AnimalCareMixin {
  @Unique private boolean reterra$wasReady;

  @Inject(method = "mobInteract", at = @At("HEAD"))
  private void reterra$beforeFeeding(
      Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> ci) {
    Animal animal = (Animal) (Object) this;
    reterra$wasReady =
        !animal.isBaby()
            && !animal.isInLove()
            && animal.isFood(player.getItemInHand(hand));
  }

  @Inject(method = "mobInteract", at = @At("RETURN"))
  private void reterra$afterFeeding(
      Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> ci) {
    Animal animal = (Animal) (Object) this;
    if (reterra$wasReady
        && ci.getReturnValue().consumesAction()
        && animal.isInLove()
        && dev.reterra.compat.WorldAccess.entityWorld(animal) instanceof ServerLevel world)
      dev.reterra.machine.Stewardship.credit(world, animal.blockPosition(), "feed");
  }
}
