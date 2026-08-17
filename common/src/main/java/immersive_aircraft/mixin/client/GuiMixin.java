package immersive_aircraft.mixin.client;

import immersive_aircraft.Main;
import immersive_aircraft.client.OverlayRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class GuiMixin {
    @Inject(method = "extractItemHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void ia$renderInject(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Main.MOD_LOADER.equals("fabric")) {
            OverlayRenderer.renderOverlay(guiGraphics, deltaTracker.getGameTimeDeltaTicks(), 49);
        }
    }
}
