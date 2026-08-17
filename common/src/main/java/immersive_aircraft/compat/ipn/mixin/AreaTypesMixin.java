package immersive_aircraft.compat.ipn.mixin;

import immersive_aircraft.compat.ipn.VehicleSlotHints;
import net.minecraft.world.inventory.Slot;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets Inventory Profiles Next see the vehicle slots that sit left of the screen origin.
 * <p>
 * IPN decides a slot is unusable when its x or y is negative, which is a fair assumption for hidden slots
 * but wrong for us: the side cargo panels are laid out at negative offsets on purpose. Only the reads
 * inside that check are clamped, so IPN's own layout maths still sees the real coordinates.
 * <p>
 * The target is a Kotlin lambda, so its name can change between IPN releases. Both redirects are optional;
 * if the method disappears the side panels simply go back to being skipped.
 */
@Mixin(targets = "org.anti_ad.mc.ipnext.inventory.AreaTypes", remap = false)
public class AreaTypesMixin {
    @Redirect(
            method = "_get_disabled_$lambda$0",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/inventory/Slot;x:I", opcode = Opcodes.GETFIELD),
            require = 0
    )
    private static int immersiveAircraft$sortableX(Slot slot) {
        return VehicleSlotHints.sortableCoordinate(slot, slot.x);
    }

    @Redirect(
            method = "_get_disabled_$lambda$0",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/inventory/Slot;y:I", opcode = Opcodes.GETFIELD),
            require = 0
    )
    private static int immersiveAircraft$sortableY(Slot slot) {
        return VehicleSlotHints.sortableCoordinate(slot, slot.y);
    }
}
