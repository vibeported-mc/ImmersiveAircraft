package immersive_aircraft.compat.ipn;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates the Inventory Profiles Next compatibility mixins so they only apply when IPN is actually present.
 * <p>
 * Presence is decided by looking for one of IPN's own classes rather than by asking the mod loader, which
 * keeps this free of any loader specific API.
 */
public class IPNMixinPlugin implements IMixinConfigPlugin {
    /**
     * Whether the class exists, without loading it. Loading a mixin target from inside the plugin happens
     * before Mixin gets a chance to transform it, which makes the target permanently untransformable
     * ("was loaded too early"), so this deliberately only looks for the class file as a resource.
     */
    private static boolean classExists(String className) {
        String resource = className.replace('.', '/') + ".class";
        return IPNMixinPlugin.class.getClassLoader().getResource(resource) != null;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    /**
     * Checked per target rather than once for IPN as a whole, so each mixin stands or falls on its own: if a
     * future IPN keeps one of the classes we hook and renames the other, the surviving mixin still applies
     * and the other is skipped instead of failing against a target that no longer exists.
     */
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return classExists(targetClassName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
