package com.angryalchemist.ecstatic.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Extends the vanilla cloud layer out past the vanilla cloud range so it meets the LOD terrain. */
final class LodCloudExtension {
    private static final Identifier CLOUDS_LOCATION = Identifier.withDefaultNamespace("textures/environment/clouds.png");
    private static final int TILE_UNITS = 32;
    private static final float HIGH_ALTITUDE_MARGIN_BLOCKS = 64.0F;
    private static final float TEXEL = 0.00390625F;
    private static GpuMesh mesh;
    private static int lastGridX = Integer.MIN_VALUE;
    private static int lastGridZ = Integer.MIN_VALUE;
    private static int lastHalfExtentUnits = -1;
    private static boolean lastFillCenterGap = false;

    private LodCloudExtension() {
    }

    static void render(
        RenderPass pass,
        LodGpu.FrameState state,
        Matrix4f rotationOnlyMatrix,
        Vec3 cameraPos,
        float cloudHeight,
        int cloudColorArgb,
        long gameTime,
        float partialTick,
        int outerRadiusBlocks
    ) {
        if (Minecraft.getInstance().options.cloudStatus().get() == CloudStatus.OFF || Float.isNaN(cloudHeight) || ARGB.alpha(cloudColorArgb) <= 0) {
            return;
        }

        double scroll = ((double)gameTime + partialTick) * 0.03;
        double d2 = (cameraPos.x + scroll) / 12.0;
        double d3 = cloudHeight - cameraPos.y + 0.33;
        double d4 = cameraPos.z / 12.0 + 0.33;
        d2 -= Math.floor(d2 / 2048.0) * 2048.0;
        d4 -= Math.floor(d4 / 2048.0) * 2048.0;
        float fracX = (float)(d2 - Math.floor(d2));
        float fracZ = (float)(d4 - Math.floor(d4));
        int gridX = (int)Math.floor(d2);
        int gridZ = (int)Math.floor(d4);
        int requestedHalfUnits = (int)Math.ceil(outerRadiusBlocks / 12.0F);
        boolean fillCenterGap = cameraPos.y > cloudHeight + HIGH_ALTITUDE_MARGIN_BLOCKS;
        int halfExtentUnits = (ceilDiv(requestedHalfUnits) + 1) * TILE_UNITS;
        if (mesh == null || gridX != lastGridX || gridZ != lastGridZ || halfExtentUnits != lastHalfExtentUnits || fillCenterGap != lastFillCenterGap) {
            rebuild(gridX, gridZ, halfExtentUnits, fillCenterGap);
            lastGridX = gridX;
            lastGridZ = gridZ;
            lastHalfExtentUnits = halfExtentUnits;
            lastFillCenterGap = fillCenterGap;
        }

        if (mesh == null) {
            return;
        }

        float savedBrightness = state.brightness;
        state.brightness = 1.0F;
        state.setColor(ARGB.redFloat(cloudColorArgb), ARGB.greenFloat(cloudColorArgb), ARGB.blueFloat(cloudColorArgb));
        Matrix4f transform = new Matrix4f(rotationOnlyMatrix);
        transform.scale(12.0F, 1.0F, 12.0F);
        transform.translate(-fracX, (float)d3, -fracZ);
        LodGpu.drawMeshes(
            pass,
            state,
            LodGpu.Program.CLOUD,
            LodGpu.Shape.TRANSLUCENT_DEPTH,
            List.of(mesh),
            Minecraft.getInstance().getTextureManager().getTexture(CLOUDS_LOCATION).getTextureView(),
            LodGpu.repeatSampler(),
            transform
        );
        state.brightness = savedBrightness;
        state.setColor(1.0F, 1.0F, 1.0F);
    }

    private static int ceilDiv(int numerator) {
        return (numerator + TILE_UNITS - 1) / TILE_UNITS;
    }

    private static void rebuild(int gridX, int gridZ, int halfExtentUnits, boolean fillCenterGap) {
        close();
        int tilesPerAxis = (halfExtentUnits * 2) / TILE_UNITS;
        int maxVertices = tilesPerAxis * tilesPerAxis * 6;
        float uOffset = gridX * TEXEL;
        float vOffset = gridZ * TEXEL;
        try (GpuMesh.Builder builder = GpuMesh.begin(DefaultVertexFormat.POSITION_TEX_COLOR, maxVertices)) {
            BufferBuilder vertices = builder.vertices();
            for (int tileX = -halfExtentUnits; tileX < halfExtentUnits; tileX += TILE_UNITS) {
                for (int tileZ = -halfExtentUnits; tileZ < halfExtentUnits; tileZ += TILE_UNITS) {
                    boolean coveredByVanilla = !fillCenterGap && tileX >= -TILE_UNITS && tileX < TILE_UNITS && tileZ >= -TILE_UNITS && tileZ < TILE_UNITS;
                    if (!coveredByVanilla) {
                        emitTile(vertices, tileX, tileZ, uOffset, vOffset);
                    }
                }
            }

            mesh = builder.upload("Ecstatic cloud extension");
        }
    }

    private static void emitTile(BufferBuilder builder, int x, int z, float uOffset, float vOffset) {
        float x1 = x + TILE_UNITS;
        float z1 = z + TILE_UNITS;
        float u0 = x * TEXEL + uOffset;
        float u1 = x1 * TEXEL + uOffset;
        float v0 = z * TEXEL + vOffset;
        float v1 = z1 * TEXEL + vOffset;
        vertex(builder, x, z1, u0, v1);
        vertex(builder, x1, z1, u1, v1);
        vertex(builder, x1, z, u1, v0);
        vertex(builder, x, z1, u0, v1);
        vertex(builder, x1, z, u1, v0);
        vertex(builder, x, z, u0, v0);
    }

    private static void vertex(BufferBuilder builder, float x, float z, float u, float v) {
        builder.addVertex(x, 0.0F, z).setUv(u, v).setColor(1.0F, 1.0F, 1.0F, 0.8F);
    }

    static void close() {
        if (mesh != null) {
            mesh.close();
            mesh = null;
        }
    }
}
