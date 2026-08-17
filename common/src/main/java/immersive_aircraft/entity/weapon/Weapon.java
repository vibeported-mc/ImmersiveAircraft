package immersive_aircraft.entity.weapon;

import org.joml.Vector3f;
import org.joml.Vector4f;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.entity.misc.WeaponMount;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public abstract class Weapon {
    private final VehicleEntity entity;
    private final ItemStack stack;
    private final WeaponMount mount;
    private final int slot;
    private int gunnerOffset;

    public Weapon(VehicleEntity entity, ItemStack stack, WeaponMount mount, int slot) {
        this.entity = entity;
        this.stack = stack;
        this.mount = mount;
        this.slot = slot;
    }

    public VehicleEntity getEntity() {
        return entity;
    }

    public ItemStack getStack() {
        return stack;
    }

    public WeaponMount getMount() {
        return mount;
    }

    public int getSlot() {
        return slot;
    }

    public int getGunnerOffset() {
        return gunnerOffset;
    }

    public void setGunnerOffset(int gunnerOffset) {
        this.gunnerOffset = gunnerOffset;
    }

    protected Vector4f getBarrelOffset() {
        return new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
    }

    /**
     * World position the projectile leaves from, before the barrel length is applied.
     */
    public Vec3 getBarrelPosition() {
        Vector4f position = getBarrelOffset();
        position.mul(getMount().transform());
        position.mul(getEntity().getVehicleTransform());
        return new Vec3(position.x(), position.y(), position.z());
    }

    /**
     * Whether shots should be aimed at whatever the gunner's reticle is on, instead of being fired along
     * the barrel's own orientation. Only sensible for weapons whose aim actually follows the gunner;
     * a fixed forward mount is supposed to shoot where the vehicle points, not where the pilot looks.
     */
    public boolean convergesOnReticle() {
        return false;
    }

    public abstract void tick();

    public abstract void fire(Vector3f direction);

    public abstract void clientFire(int index);

    public <T extends VehicleEntity> void setAnimationVariables(T entity, float time) {
        // nop
    }
}
