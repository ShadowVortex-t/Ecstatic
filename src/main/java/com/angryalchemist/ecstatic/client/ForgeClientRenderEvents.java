package com.angryalchemist.ecstatic.client;

import com.angryalchemist.ecstatic.debug.LodDebugCommon;
import com.angryalchemist.ecstatic.debug.LodDebugCompare;
import com.angryalchemist.ecstatic.debug.LodDebugState;
import com.angryalchemist.ecstatic.render.BiomeStyleScreen;
import com.angryalchemist.ecstatic.render.LodRenderer;
import com.angryalchemist.ecstatic.render.LodSettingsScreen;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent.RenderFog;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "ecstatic", value = Dist.CLIENT)
public final class ForgeClientRenderEvents {
    private static final float FOG_DISABLE_MULTIPLIER = 100.0F;

    private ForgeClientRenderEvents() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentParticles event) {
        LodRenderer.render(event.getRenderPass(), event.getLevelRenderState());
    }

    @SubscribeEvent
    public static void onRenderFog(RenderFog event) {
        if (event.getType() == FogType.NONE && LodRenderer.isOverworldLoaded()) {
            Camera camera = event.getCamera();
            if (!(camera.entity() instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS)))) {
                Minecraft client = Minecraft.getInstance();
                Vec3 pos = camera.position();
                boolean isFoggy = client.level.effects().isFoggyAt(Mth.floor(pos.x), Mth.floor(pos.y))
                    || client.gui.getBossOverlay().shouldCreateWorldFog();
                if (!isFoggy) {
                    // push the vanilla render-distance fog far out so it doesn't hide the LOD terrain
                    FogData fog = event.getFogData();
                    float renderDistanceEnd = fog.renderDistanceEnd;
                    fog.renderDistanceStart = renderDistanceEnd * FOG_DISABLE_MULTIPLIER;
                    fog.renderDistanceEnd = renderDistanceEnd * FOG_DISABLE_MULTIPLIER * 12.0F;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        boolean debugTools = LodDebugState.isEnabled();

        while (ForgeClientKeyBindings.CYCLE_VERTEX_FORMAT_KEY.consumeClick()) {
            if (debugTools) {
                int override = LodDebugState.cycleVertexFormatOverride();
                LodDebugCommon.sendMessage(client, "vertex format override: " + vertexFormatOverrideLabel(override));
                LodRenderer.rebuildAllMeshes();
            }
        }

        while (ForgeClientKeyBindings.CYCLE_FORCED_LOD_KEY.consumeClick()) {
            if (debugTools) {
                int level = LodDebugState.cycleForcedLevel();
                LodDebugCommon.sendMessage(client, "forced level: " + (level == 0 ? "off (LOD1-4)" : "LOD" + level + " only"));
            }
        }

        while (ForgeClientKeyBindings.TOGGLE_REFERENCE_QUAD_KEY.consumeClick()) {
            if (debugTools) {
                boolean enabled = LodDebugState.toggleReferenceQuad();
                LodDebugCommon.sendMessage(client, "debug reference quad: " + (enabled ? "on (look down)" : "off"));
            }
        }

        while (ForgeClientKeyBindings.COMPARE_SAMPLE_KEY.consumeClick()) {
            if (debugTools) {
                LodDebugCompare.compareAtPlayer(client);
            }
        }

        while (ForgeClientKeyBindings.OPEN_BIOME_STYLE_KEY.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(new BiomeStyleScreen());
            }
        }

        while (ForgeClientKeyBindings.OPEN_SETTINGS_KEY.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(new LodSettingsScreen());
            }
        }
    }


    static String vertexFormatOverrideLabel(int override) {
        return switch (override) {
            case 1 -> "forced LIT";
            case 2 -> "forced CHEAP (position-color)";
            default -> "auto (config default / forced LIT if a shaderpack is active)";
        };
    }
}
