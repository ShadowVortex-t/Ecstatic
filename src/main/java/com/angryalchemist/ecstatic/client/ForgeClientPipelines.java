package com.angryalchemist.ecstatic.client;

import com.angryalchemist.ecstatic.render.LodGpu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

@EventBusSubscriber(modid = "ecstatic", value = Dist.CLIENT)
public final class ForgeClientPipelines {
    private ForgeClientPipelines() {
    }

    @SubscribeEvent
    public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        LodGpu.registerPipelines(event);
    }
}
