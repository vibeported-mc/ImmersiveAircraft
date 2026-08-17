package immersive_aircraft.compat.ipn;

import immersive_aircraft.entity.inventory.SparseSimpleInventory;
import net.minecraft.world.inventory.Slot;

import java.util.Set;

/**
 * Shared logic for the Inventory Profiles Next compatibility mixins. Plain class rather than part of a
 * mixin so it carries ordinary static initialisation.
 */
public final class VehicleSlotHints {
    /**
     * Slots that restrict what they accept, but accept more than one kind of item: coal and logs are both
     * fuel, any upgrade fits any upgrade slot. Sorting treats them as interchangeable storage and silently
     * swaps their contents, changing a vehicle's fuel or shuffling its weapons.
     */
    private static final Set<String> PROTECTED_SLOT_CLASSES = Set.of(
            "immersive_aircraft.screen.slot.FuelSlot",
            "immersive_aircraft.screen.slot.UpgradeSlot",
            "immersive_aircraft.screen.slot.TypedSlot",
            "immersive_aircraft.screen.slot.IngredientSlot"
    );

    private VehicleSlotHints() {
    }

    public static boolean isProtectedSlot(String slotClassName) {
        return PROTECTED_SLOT_CLASSES.contains(slotClassName);
    }

    /**
     * Vehicle screens place their side panels to the left of the window origin, so those slots carry
     * negative coordinates. IPN reads a negative coordinate as "offscreen" and skips the slot entirely,
     * which is why cargo in the left hand panels never sorted. Clamping only affects that check, and only
     * for vehicle slots, so other mods' genuinely hidden slots keep being skipped.
     */
    public static int sortableCoordinate(Slot slot, int coordinate) {
        return slot.container instanceof SparseSimpleInventory ? Math.max(0, coordinate) : coordinate;
    }
}
