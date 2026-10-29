package com.angryalchemist.ecstatic.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "ecstatic", value = Dist.CLIENT)
public final class ForgeClientKeyBindings {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath("ecstatic", "main"));
    public static final KeyMapping CYCLE_VERTEX_FORMAT_KEY = new KeyMapping("key.ecstatic.cycle_vertex_format", Type.KEYBOARD, 298, CATEGORY);
    public static final KeyMapping CYCLE_FORCED_LOD_KEY = new KeyMapping("key.ecstatic.cycle_forced_lod", Type.KEYBOARD, 299, CATEGORY);
    public static final KeyMapping TOGGLE_REFERENCE_QUAD_KEY = new KeyMapping("key.ecstatic.toggle_reference_quad", Type.KEYBOARD, 297, CATEGORY);
    public static final KeyMapping COMPARE_SAMPLE_KEY = new KeyMapping("key.ecstatic.compare_sample", Type.KEYBOARD, 296, CATEGORY);
    public static final KeyMapping OPEN_BIOME_STYLE_KEY = new KeyMapping("key.ecstatic.open_biome_style", Type.KEYBOARD, 295, CATEGORY);
    public static final KeyMapping OPEN_SETTINGS_KEY = new KeyMapping("key.ecstatic.open_settings", Type.KEYBOARD, 293, CATEGORY);

    private ForgeClientKeyBindings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        com.angryalchemist.ecstatic.Constants.LOG.info("Ecstatic: registering key mappings");
        event.registerCategory(CATEGORY);
        event.register(CYCLE_VERTEX_FORMAT_KEY);
        event.register(CYCLE_FORCED_LOD_KEY);
        event.register(TOGGLE_REFERENCE_QUAD_KEY);
        event.register(COMPARE_SAMPLE_KEY);
        event.register(OPEN_BIOME_STYLE_KEY);
        event.register(OPEN_SETTINGS_KEY);
    }
}
