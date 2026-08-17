package immersive_aircraft.mixin.client;

import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    public abstract Entity entity();

    // 26.2 folded Camera#setup into #alignWithEntity, which is where the third person zoom is applied.
    @Inject(method = "alignWithEntity(F)V", at = @At("TAIL"))
    public void ia$alignWithEntity(float tickDelta, CallbackInfo ci) {
        Entity entity = entity();
        if (detached && entity != null && entity.getVehicle() instanceof VehicleEntity vehicle) {
            // Pass the camera's partial tick so an animated zoom interpolates per frame rather than per tick.
            move(-getMaxZoom((float) vehicle.getZoom(tickDelta)), 0.0f, 0.0f);
        }
    }

    @Shadow
    protected abstract void move(float zoom, float dy, float dx);

    @Shadow
    protected abstract float getMaxZoom(float maxZoom);
}
