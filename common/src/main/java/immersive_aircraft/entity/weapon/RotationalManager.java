package immersive_aircraft.entity.weapon;

import com.mojang.math.Axis;
import immersive_aircraft.Main;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.Optional;

public class RotationalManager {
    /**
     * How far the reticle ray is traced before shots are treated as effectively parallel to the view.
     */
    private static final double AIM_RANGE = 192.0;

    /**
     * Entities are only searched for over a shorter span; past this the reticle is effectively on terrain
     * or sky, and scanning the whole ray for entities is pure cost.
     */
    private static final double ENTITY_AIM_RANGE = 64.0;

    private final Weapon weapon;

    private Vec3 cachedAim;
    private long cachedAimTick = Long.MIN_VALUE;

    float yaw = 0.0f;
    float pitch = 0.0f;
    float roll = 0.0f;

    float lastYaw = 0.0f;
    float lastPitch = 0.0f;
    float lastRoll = 0.0f;

    public RotationalManager(Weapon weapon) {
        this.weapon = weapon;
    }

    private float turn(float diff) {
        if (diff > Math.PI) {
            diff -= (float) (Math.PI * 2.0f);
        } else if (diff < -Math.PI) {
            diff += (float) (Math.PI * 2.0f);
        }
        return diff;
    }

    public float getPitch(float tickDelta) {
        float diff = turn(pitch - lastPitch);
        return lastPitch + diff * tickDelta;
    }

    public float getYaw(float tickDelta) {
        float diff = turn(yaw - lastYaw);
        return lastYaw + diff * tickDelta;
    }

    public float getRoll(float tickDelta) {
        float diff = turn(roll - lastRoll);
        return lastRoll + diff * tickDelta;
    }

    public void tick() {
        lastYaw = yaw;
        lastPitch = pitch;
        lastRoll = roll;
    }

    public Matrix3f getCamera(VehicleEntity vehicle, Entity pilot) {
        Matrix3f camera = new Matrix3f();

        if (vehicle.adaptPlayerRotation && Main.firstPersonGetter.isFirstPerson()) {
            camera.rotate(Axis.ZP.rotationDegrees(vehicle.getRoll()));
            camera.rotate(Axis.XP.rotationDegrees(vehicle.getXRot()));
        }

        camera.rotate(Axis.XP.rotationDegrees(pilot.getXRot()));
        camera.rotate(Axis.YP.rotationDegrees(pilot.getYRot() + 180.0f));

        return camera;
    }

    public void pointTo(VehicleEntity vehicle) {
        // These angles only drive the model animation, and the shot direction is sent up by the client
        // anyway, so the server has no reason to pay for tracing the reticle every tick.
        if (!vehicle.level().isClientSide()) {
            pointTo(vehicle, new Vector3f(0.0f, 0.0f, -1.0f));
            return;
        }

        // Aim the model at the point the shots converge on, so the barrel matches where rounds actually go.
        Vector3f aim = aimFrom(vehicle, weapon.getBarrelPosition());
        applyWorldNormal(vehicle, aim == null ? screenToGlobal(vehicle) : aim);
    }

    public void pointTo(VehicleEntity vehicle, Vector3f normal) {
        applyWorldNormal(vehicle, screenToGlobal(vehicle, normal));
    }

    private void applyWorldNormal(VehicleEntity vehicle, Vector3f normal) {
        // Convert into vehicle space
        Matrix3f vehicleTransform = new Matrix3f(vehicle.getVehicleNormalTransform());
        vehicleTransform.invert();
        normal.mul(vehicleTransform);

        // Convert into weapon space
        Matrix3f weaponTransform = new Matrix3f(weapon.getMount().transform());
        weaponTransform.invert();
        normal.mul(weaponTransform);

        yaw = (float) -Math.atan2(normal.x(), normal.z());
        pitch = (float) -Math.atan2(normal.y(), Math.sqrt(normal.x() * normal.x() + normal.z() * normal.z()));
    }

    public Vector3f screenToGlobal(VehicleEntity vehicle) {
        return screenToGlobal(vehicle, new Vector3f(0.0f, 0.0f, -1.0f));
    }

    /**
     * The world position the gunner's reticle is resting on: the nearest block or entity along the view
     * ray, or a far point along it when the reticle is on open sky.
     * <p>
     * The ray starts at the gunner's eye rather than the camera, which is both how vanilla decides what
     * the crosshair is pointing at and the only origin that exists on the server as well as the client.
     */
    public Vec3 getAimPoint(VehicleEntity vehicle) {
        Entity gunner = vehicle.getGunner(weapon.getGunnerOffset());
        if (gunner == null) {
            return null;
        }

        // Tracing the reticle is far more expensive than the matrix work around it, and both the turret
        // animation and every shot in a burst want the same answer, so resolve it once per tick.
        long tick = vehicle.level().getGameTime();
        if (tick == cachedAimTick) {
            return cachedAim;
        }
        cachedAimTick = tick;

        Vector3f view = screenToGlobal(vehicle);
        Vec3 from = gunner.getEyePosition();
        Vec3 to = from.add(view.x() * AIM_RANGE, view.y() * AIM_RANGE, view.z() * AIM_RANGE);

        BlockHitResult blockHit = vehicle.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, gunner));
        Vec3 aim = blockHit.getType() == HitResult.Type.MISS ? to : blockHit.getLocation();

        // Prefer an entity in front of that block, ignoring our own vehicle and anyone riding it. The search
        // box is bounded separately: aiming at open sky would otherwise sweep entities for the full range.
        Vec3 entityLimit = from.add(view.x() * ENTITY_AIM_RANGE, view.y() * ENTITY_AIM_RANGE, view.z() * ENTITY_AIM_RANGE);
        Vec3 entityEnd = from.distanceToSqr(aim) < from.distanceToSqr(entityLimit) ? aim : entityLimit;

        cachedAim = traceEntity(vehicle, gunner, from, entityEnd).orElse(aim);
        return cachedAim;
    }

    /**
     * Nearest entity along the aim ray.
     * <p>
     * Deliberately not {@code ProjectileUtil#getEntityHitResult}: that path is mixed into so projectiles can
     * collide with vehicle sub-hitboxes, and it widens the query box by another 16 blocks. That is fine for a
     * projectile stepping a block at a time, but ruinous for an aim ray that is tens of blocks long and
     * re-traced every tick.
     */
    private Optional<Vec3> traceEntity(VehicleEntity vehicle, Entity gunner, Vec3 from, Vec3 to) {
        Vec3 nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Entity target : vehicle.level().getEntities(gunner, new AABB(from, to).inflate(1.0),
                candidate -> !candidate.isSpectator() && candidate.isPickable()
                        && candidate != vehicle && candidate.getRootVehicle() != vehicle)) {
            Optional<Vec3> hit = target.getBoundingBox().inflate(0.3).clip(from, to);
            if (hit.isPresent()) {
                double distance = from.distanceToSqr(hit.get());
                if (distance < nearestDistance) {
                    nearest = hit.get();
                    nearestDistance = distance;
                }
            }
        }

        return Optional.ofNullable(nearest);
    }

    /**
     * Direction from a muzzle to the reticle's aim point, so shots converge on the crosshair instead of
     * running parallel to it. Returns null when nobody is aboard to aim, leaving the fallback to the caller.
     */
    public Vector3f aimFrom(VehicleEntity vehicle, Vec3 muzzle) {
        Vec3 aim = getAimPoint(vehicle);
        if (aim == null) {
            return null;
        }

        Vector3f direction = new Vector3f(
                (float) (aim.x - muzzle.x),
                (float) (aim.y - muzzle.y),
                (float) (aim.z - muzzle.z)
        );

        // Degenerate if the muzzle is sitting on the aim point; keep the view direction in that case.
        if (direction.lengthSquared() < 1.0e-6f) {
            return screenToGlobal(vehicle);
        }

        return direction.normalize();
    }

    public Vector3f screenToGlobal(VehicleEntity vehicle, Vector3f normal) {
        Entity pilot = vehicle.getGunner(weapon.getGunnerOffset());

        if (pilot != null) {
            Matrix3f camera = getCamera(vehicle, pilot);
            camera.invert();
            normal.mul(camera);
        }

        return normal;
    }
}
