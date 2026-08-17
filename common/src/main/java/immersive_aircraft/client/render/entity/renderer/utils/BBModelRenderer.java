package immersive_aircraft.client.render.entity.renderer.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.resources.bbmodel.*;
import immersive_aircraft.util.Utils;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class BBModelRenderer {
    public interface RenderTypeProvider {
        RenderType getRenderType(BBFaceContainer container, BBFace face);
    }

    public static final RenderTypeProvider DEFAULT_RENDER_TYPE_PROVIDER = (container, face) -> RenderTypes.entityCutout(face.texture.location, !container.enableCulling());

    public static <T extends VehicleEntity> void renderModel(BBModel model, PoseStack matrixStack, SubmitNodeCollector collector, int light, float time, T entity, ModelPartRenderHandler<T> modelPartRenderer, float red, float green, float blue, float alpha) {
        model.root.forEach(object -> renderObject(model, object, matrixStack, collector, light, time, entity, modelPartRenderer, red, green, blue, alpha));
    }

    /**
     * Apply transformations, animations, and callbacks, and render the object.
     */
    public static <T extends VehicleEntity> void renderObject(BBModel model, BBObject object, PoseStack matrixStack, SubmitNodeCollector collector, int light, float time, T entity, ModelPartRenderHandler<T> modelPartRenderer, float red, float green, float blue, float alpha) {
        matrixStack.pushPose();
        matrixStack.translate(object.origin.x(), object.origin.y(), object.origin.z());

        // Apply animations
        if (!model.animations.isEmpty()) {
            BBAnimation animation = model.animations.get(0);
            if (animation.hasAnimator(object.uuid)) {
                Vector3f position = animation.sample(object.uuid, BBAnimator.Channel.POSITION, time);
                position.mul(1.0f / 16.0f);
                matrixStack.translate(position.x(), position.y(), position.z());

                Vector3f rotation = animation.sample(object.uuid, BBAnimator.Channel.ROTATION, time);
                rotation.mul(1.0f / 180.0f * (float) Math.PI);
                matrixStack.mulPose(Utils.fromXYZ(rotation));

                Vector3f scale = animation.sample(object.uuid, BBAnimator.Channel.SCALE, time);
                matrixStack.scale(scale.x(), scale.y(), scale.z());
            }
        }

        // Apply object rotation
        matrixStack.mulPose(Utils.fromXYZ(object.rotation));

        // Apply additional, complex animations
        if (object instanceof BBBone bone && modelPartRenderer != null) {
            modelPartRenderer.animate(bone.name, entity, matrixStack, time);
        }

        // The bones origin is only used during transformation
        if (object instanceof BBBone) {
            matrixStack.translate(-object.origin.x(), -object.origin.y(), -object.origin.z());
        }

        // Render the object
        if (modelPartRenderer == null || !modelPartRenderer.render(object.name, model, object, collector, entity, matrixStack, light, time, modelPartRenderer)) {
            renderObjectInner(model, object, matrixStack, collector, light, time, entity, modelPartRenderer, red, green, blue, alpha);
        }

        matrixStack.popPose();
    }

    /**
     * Render the object without applying transformations, animations, or callbacks.
     */
    public static <T extends VehicleEntity> void renderObjectInner(BBModel model, BBObject object, PoseStack matrixStack, SubmitNodeCollector collector, int light, float time, T entity, ModelPartRenderHandler<T> modelPartRenderer, float red, float green, float blue, float alpha) {
        if (object instanceof BBFaceContainer cube) {
            renderFaces(cube, matrixStack, collector, light, red, green, blue, alpha, modelPartRenderer == null ? DEFAULT_RENDER_TYPE_PROVIDER : modelPartRenderer.getRenderTypeProvider());
        } else if (object instanceof BBBone bone) {
            boolean shouldRender = bone.visibility;
            if (bone.name.equals("lod0")) {
                shouldRender = entity.isWithinParticleRange();
            } else if (bone.name.equals("lod1")) {
                shouldRender = !entity.isWithinParticleRange();
            }

            if (shouldRender) {
                bone.children.forEach(child -> renderObject(model, child, matrixStack, collector, light, time, entity, modelPartRenderer, red, green, blue, alpha));
            }
        }
    }

    public static void renderFaces(BBFaceContainer cube, PoseStack matrixStack, SubmitNodeCollector collector, int light, float red, float green, float blue, float alpha, RenderTypeProvider provider) {
        int color = ARGB.colorFromFloat(alpha, red, green, blue);

        // Geometry is submitted for deferred drawing, so faces sharing a render type go into one submission.
        // This runs for every object of every visible vehicle each frame, so the common case where a
        // container is single-textured is handled without allocating any collections. Render types are
        // memoized by Minecraft, so identity comparison is enough to tell them apart.
        RenderType uniformType = null;
        boolean uniform = true;
        for (BBFace face : cube.getFaces()) {
            RenderType type = provider.getRenderType(cube, face);
            if (uniformType == null) {
                uniformType = type;
            } else if (uniformType != type) {
                uniform = false;
                break;
            }
        }

        if (uniformType == null) {
            return;
        }

        if (uniform) {
            collector.submitCustomGeometry(matrixStack, uniformType, (pose, vertexConsumer) -> {
                for (BBFace face : cube.getFaces()) {
                    emitFace(vertexConsumer, pose, face, color, light);
                }
            });
            return;
        }

        // Mixed textures within one container: one submission per distinct render type.
        List<RenderType> submitted = new ArrayList<>();
        for (BBFace face : cube.getFaces()) {
            final RenderType type = provider.getRenderType(cube, face);
            if (submitted.contains(type)) {
                continue;
            }
            submitted.add(type);
            collector.submitCustomGeometry(matrixStack, type, (pose, vertexConsumer) -> {
                for (BBFace candidate : cube.getFaces()) {
                    if (provider.getRenderType(cube, candidate) == type) {
                        emitFace(vertexConsumer, pose, candidate, color, light);
                    }
                }
            });
        }
    }

    private static void emitFace(VertexConsumer vertexConsumer, PoseStack.Pose pose, BBFace face, int color, int light) {
        for (int i = 0; i < 4; i++) {
            BBFace.BBVertex v = face.vertices[i];
            vertexConsumer.addVertex(pose, v.x, v.y, v.z)
                    .setColor(color)
                    .setUv(v.u, v.v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, v.nx, v.ny, v.nz);
        }
    }

    public static void renderBanner(BBFaceContainer cube, PoseStack matrixStack, SubmitNodeCollector collector, SpriteGetter sprites, int light, boolean isBanner, DyeColor baseColor, List<BannerPatternLayers.Layer> patterns) {
        matrixStack.pushPose();

        if (cube instanceof BBObject object) {
            matrixStack.translate(object.origin.x(), object.origin.y(), object.origin.z());
        }

        // Render the base material
        SpriteId baseSprite = isBanner ? Sheets.BANNER_BASE : Sheets.SHIELD_BASE;
        renderBannerSprite(cube, matrixStack, collector, sprites, light, baseColor, baseSprite);

        // And the patterns
        for (BannerPatternLayers.Layer pattern : patterns) {
            SpriteId sprite = isBanner ? Sheets.getBannerSprite(pattern.pattern()) : Sheets.getShieldSprite(pattern.pattern());
            renderBannerSprite(cube, matrixStack, collector, sprites, light, pattern.color(), sprite);
        }

        matrixStack.popPose();
    }

    private static void renderBannerSprite(BBFaceContainer cube, PoseStack matrixStack, SubmitNodeCollector collector, SpriteGetter sprites, int light, DyeColor color, SpriteId spriteId) {
        int fs = color.getTextureDiffuseColor();
        float r = ((fs >> 16) & 0xFF) / 255.0f;
        float g = ((fs >> 8) & 0xFF) / 255.0f;
        float b = (fs & 0xFF) / 255.0f;

        // Banner patterns live on a stitched atlas, so the model's UVs have to be mapped into the sprite's region.
        TextureAtlasSprite sprite = sprites.get(spriteId);
        RenderType renderType = spriteId.renderType(RenderTypes::entityTranslucent);
        int packedColor = ARGB.colorFromFloat(1.0f, r, g, b);

        collector.submitCustomGeometry(matrixStack, renderType, (pose, vertexConsumer) -> {
            for (BBFace face : cube.getFaces()) {
                for (int i = 0; i < 4; i++) {
                    BBFace.BBVertex v = face.vertices[i];
                    vertexConsumer.addVertex(pose, v.x, v.y, v.z)
                            .setColor(packedColor)
                            .setUv(sprite.getU(v.u), sprite.getV(v.v))
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light)
                            .setNormal(pose, v.nx, v.ny, v.nz);
                }
            }
        });
    }

    public static void renderSailObject(BBMesh cube, PoseStack matrixStack, SubmitNodeCollector collector, int light, float time, float red, float green, float blue, float alpha) {
        renderSailObject(cube, matrixStack, collector, light, time, red, green, blue, alpha, 0.025f, 0.0f);
    }

    public static void renderSailObject(BBMesh cube, PoseStack matrixStack, SubmitNodeCollector collector, int light, float time, float red, float green, float blue, float alpha, float distanceScale, float baseScale) {
        // Sails are single-textured in practice; group by render type the same allocation-free way as renderFaces.
        RenderType uniformType = null;
        boolean uniform = true;
        for (BBFace face : cube.getFaces()) {
            RenderType type = RenderTypes.entityCutout(face.texture.location, true);
            if (uniformType == null) {
                uniformType = type;
            } else if (uniformType != type) {
                uniform = false;
                break;
            }
        }

        if (uniformType == null) {
            return;
        }

        if (!uniform) {
            // Mixed textures: fall back to one pass per distinct render type.
            List<RenderType> submitted = new ArrayList<>();
            for (BBFace face : cube.getFaces()) {
                RenderType type = RenderTypes.entityCutout(face.texture.location, true);
                if (submitted.contains(type)) {
                    continue;
                }
                submitted.add(type);
                renderSailBatch(cube, matrixStack, collector, type, light, time, red, green, blue, alpha, distanceScale, baseScale);
            }
            return;
        }

        renderSailBatch(cube, matrixStack, collector, uniformType, light, time, red, green, blue, alpha, distanceScale, baseScale);
    }

    private static void renderSailBatch(BBMesh cube, PoseStack matrixStack, SubmitNodeCollector collector, RenderType renderType, int light, float time, float red, float green, float blue, float alpha, float distanceScale, float baseScale) {
        collector.submitCustomGeometry(matrixStack, renderType, (pose, vertexConsumer) -> {
            for (BBFace face : cube.getFaces()) {
                if (RenderTypes.entityCutout(face.texture.location, true) != renderType) {
                    continue;
                }
                for (int i = 0; i < 4; i++) {
                    BBFace.BBVertex v = face.vertices[i];
                    float distance = Math.max(
                            Math.max(
                                    Math.abs(v.x),
                                    Math.abs(v.y)
                            ),
                            Math.abs(v.z)
                    );
                    double angle = (v.x + v.z + v.y * 0.25) * 4.0f + time * 4.0f;
                    double scale = distanceScale * distance + baseScale;
                    float x = (float) ((Math.cos(angle) + Math.cos(angle * 1.7)) * scale);
                    float z = (float) ((Math.sin(angle) + Math.sin(angle * 1.7)) * scale);

                    vertexConsumer
                            .addVertex(pose, v.x + x, v.y, v.z + z)
                            .setColor(red, green, blue, alpha)
                            .setUv(v.u, v.v)
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light)
                            .setNormal(pose, v.nx, v.ny, v.nz);
                }
            }
        });
    }
}
