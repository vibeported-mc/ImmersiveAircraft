package immersive_aircraft.compat.ipn.mixin;

import immersive_aircraft.compat.ipn.VehicleSlotHints;
import org.anti_ad.mc.ipnext.integration.SlotIntegrationData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks the vehicle's typed slots as ignored by Inventory Profiles Next.
 * <p>
 * IPN already excludes a slot when {@code SlotIntegrationHints.hintFor(slotClassName).ignore} is set, but it
 * only ever populates that map from a JSON bundled inside its own jar, so a mod cannot register its slots.
 * This answers for our own slot classes and leaves every other lookup untouched.
 */
@Mixin(targets = "org.anti_ad.mc.ipnext.integration.SlotIntegrationHints", remap = false)
public class SlotIntegrationHintsMixin {
    @Inject(method = "hintFor", at = @At("RETURN"), cancellable = true, require = 0)
    private void immersiveAircraft$protectVehicleSlots(String className, CallbackInfoReturnable<SlotIntegrationData> cir) {
        if (VehicleSlotHints.isProtectedSlot(className)) {
            // A fresh instance: the default returned for unknown slots is a shared singleton.
            cir.setReturnValue(new SlotIntegrationData(true));
        }
    }
}
