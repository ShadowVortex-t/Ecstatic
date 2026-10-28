package com.angryalchemist.ecstatic.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * Pipelines, per-frame uniforms and draw helpers for the 26.x renderer.
 *
 * <p>Every Ecstatic draw uses vanilla's {@code Projection} and {@code DynamicTransforms} uniform blocks.
 * The model-view matrix and brightness are sent in their normal slots, and the extra values (fog start/end,
 * fog intensity, saturation, sun direction, game time) are packed into the otherwise unused texture matrix.
 * See {@code include/ecstatic_params.glsl}.
 */
final class LodGpu {
    /** Vertex format for the "lit" meshes: position, colour, uv, (full-bright) light uv and a normal. */
    static final VertexFormat LIT_FORMAT = VertexFormat.builder(0)
        .addAttribute("Position", GpuFormat.RGB32_FLOAT)
        .addAttribute("Color", GpuFormat.RGBA8_UNORM)
        .addAttribute("UV0", GpuFormat.RG32_FLOAT)
        .addAttribute("UV2", GpuFormat.RG16_SINT)
        .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
        .build();

    enum Program {
        FOG_PLAIN("lod_p", "terrain_fog", DefaultVertexFormat.POSITION_COLOR, false, false, false),
        FOG_TEX("lod_pt", "terrain_fog_tex", DefaultVertexFormat.POSITION_TEX_COLOR, true, false, false),
        LIT_PLAIN("lod_lit", "terrain_lit", LIT_FORMAT, false, true, false),
        LIT_TEX("lod_lit", "terrain_lit", LIT_FORMAT, true, true, false),
        PARALLAX("lod_parallax", "terrain_parallax", LIT_FORMAT, false, true, false),
        WATER_TEX("lod_water", "water_tex", DefaultVertexFormat.POSITION_TEX_COLOR, true, true, false),
        TREE("lod_pt", "tree", DefaultVertexFormat.POSITION_TEX_COLOR, true, true, false),
        CLOUD("lod_pt", "cloud_tex", DefaultVertexFormat.POSITION_TEX_COLOR, true, false, false);

        final String vertexShader;
        final String fragmentShader;
        final VertexFormat format;
        final boolean textured;
        final boolean usesFogColor;

        Program(String vertexShader, String fragmentShader, VertexFormat format, boolean textured, boolean usesFogColor, boolean unused) {
            this.vertexShader = vertexShader;
            this.fragmentShader = fragmentShader;
            this.format = format;
            this.textured = textured;
            this.usesFogColor = usesFogColor;
        }
    }

    enum Shape {
        /** Opaque, back faces culled, writes depth. */
        OPAQUE_CULL(false, true, true),
        /** Opaque, no culling, writes depth. */
        OPAQUE_NOCULL(false, false, true),
        /** Alpha blended, no culling, does not write depth. */
        FADE(true, false, false),
        /** Alpha blended, no culling, writes depth. */
        TRANSLUCENT_DEPTH(true, false, true);

        final boolean blend;
        final boolean cull;
        final boolean depthWrite;

        Shape(boolean blend, boolean cull, boolean depthWrite) {
            this.blend = blend;
            this.cull = cull;
            this.depthWrite = depthWrite;
        }
    }

    private static final Map<Program, Map<Shape, RenderPipeline>> PIPELINES = new EnumMap<>(Program.class);

    static {
        add(Program.FOG_PLAIN, Shape.OPAQUE_CULL, Shape.OPAQUE_NOCULL, Shape.FADE);
        add(Program.FOG_TEX, Shape.OPAQUE_CULL, Shape.OPAQUE_NOCULL, Shape.FADE);
        add(Program.LIT_PLAIN, Shape.FADE);
        add(Program.LIT_TEX, Shape.OPAQUE_CULL, Shape.OPAQUE_NOCULL, Shape.FADE);
        add(Program.PARALLAX, Shape.TRANSLUCENT_DEPTH);
        add(Program.WATER_TEX, Shape.FADE);
        add(Program.TREE, Shape.OPAQUE_CULL);
        add(Program.CLOUD, Shape.TRANSLUCENT_DEPTH);
    }

    private static ProjectionMatrixBuffer projectionBuffer;

    private LodGpu() {
    }

    private static void add(Program program, Shape... shapes) {
        Map<Shape, RenderPipeline> byShape = PIPELINES.computeIfAbsent(program, p -> new EnumMap<>(Shape.class));
        for (Shape shape : shapes) {
            byShape.put(shape, buildPipeline(program, shape));
        }
    }

    private static RenderPipeline buildPipeline(Program program, Shape shape) {
        String name = "pipeline/" + program.name().toLowerCase(java.util.Locale.ROOT) + "_" + shape.name().toLowerCase(java.util.Locale.ROOT);
        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("ecstatic", name))
            .withVertexShader(Identifier.fromNamespaceAndPath("ecstatic", "core/" + program.vertexShader))
            .withFragmentShader(Identifier.fromNamespaceAndPath("ecstatic", "core/" + program.fragmentShader))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withVertexBinding(0, program.format)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(shape.blend ? new ColorTargetState(BlendFunction.TRANSLUCENT) : ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, shape.depthWrite))
            .withCull(shape.cull);
        if (program.usesFogColor) {
            builder.withBindGroupLayout(BindGroupLayouts.FOG);
        }

        if (program.textured) {
            builder.withBindGroupLayout(BindGroupLayouts.SAMPLER0);
            builder.withShaderDefine("TEXTURED");
        }

        return builder.build();
    }

    static void registerPipelines(RegisterRenderPipelinesEvent event) {
        for (Map<Shape, RenderPipeline> byShape : PIPELINES.values()) {
            for (RenderPipeline pipeline : byShape.values()) {
                event.registerPipeline(pipeline);
            }
        }
    }

    static RenderPipeline pipeline(Program program, Shape shape) {
        Map<Shape, RenderPipeline> byShape = PIPELINES.get(program);
        RenderPipeline pipeline = byShape == null ? null : byShape.get(shape);
        if (pipeline == null) {
            throw new IllegalStateException("Ecstatic has no pipeline for " + program + "/" + shape);
        }

        return pipeline;
    }

    /** Uploads the extended-far-plane projection to the GPU; one value is used per frame. */
    static GpuBufferSlice uploadProjection(Matrix4f projection) {
        if (projectionBuffer == null) {
            projectionBuffer = new ProjectionMatrixBuffer("ecstatic");
        }

        return projectionBuffer.getBuffer(projection);
    }

    static GpuSampler atlasSampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, true);
    }

    static GpuSampler nearestSampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    /** Mutable per-frame draw state, mirroring the shader colour / fog uniforms of the old renderer. */
    static final class FrameState {
        Matrix4f modelView = new Matrix4f();
        GpuBufferSlice projection;
        float brightness = 1.0F;
        float colorR = 1.0F;
        float colorG = 1.0F;
        float colorB = 1.0F;
        float fogStart;
        float fogEnd = 1.0F;
        float fogIntensity = 1.0F;
        float saturation;
        final Vector3f sunDir = new Vector3f(0.0F, 1.0F, 0.0F);
        float gameTime;

        void setColor(float r, float g, float b) {
            this.colorR = r;
            this.colorG = g;
            this.colorB = b;
        }

        void setSunDir(Vector3fc dir) {
            this.sunDir.set(dir);
        }

        GpuBufferSlice writeTransform(Matrix4f modelViewOverride) {
            Matrix4f params = new Matrix4f();
            params.m00(this.fogStart).m01(this.fogEnd).m02(this.fogIntensity).m03(this.saturation);
            params.m10(this.sunDir.x).m11(this.sunDir.y).m12(this.sunDir.z).m13(this.gameTime);
            return RenderSystem.getDynamicUniforms()
                .writeTransform(
                    new DynamicGpuData.Transform(
                        modelViewOverride == null ? this.modelView : modelViewOverride,
                        new Vector4f(this.colorR * this.brightness, this.colorG * this.brightness, this.colorB * this.brightness, 1.0F),
                        new Vector3f(),
                        params
                    )
                );
        }
    }

    /** Draws every mesh in {@code meshes} with one pipeline and one set of uniforms. */
    static void drawMeshes(
        RenderPass pass,
        FrameState state,
        Program program,
        Shape shape,
        List<GpuMesh> meshes,
        GpuTextureView texture,
        GpuSampler sampler,
        Matrix4f modelViewOverride
    ) {
        if (meshes.isEmpty()) {
            return;
        }

        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline(program, shape)));
        pass.setUniform("Projection", state.projection);
        pass.setUniform("DynamicTransforms", state.writeTransform(modelViewOverride));
        if (program.usesFogColor) {
            GpuBufferSlice fog = RenderSystem.getShaderFog();
            if (fog != null) {
                pass.setUniform("Fog", fog);
            }
        }

        if (program.textured) {
            pass.setUniform("Sampler0", texture, sampler);
        }

        for (GpuMesh mesh : meshes) {
            pass.setVertexBuffer(0, mesh.buffer().slice());
            pass.draw(mesh.vertexCount(), 1, 0, 0);
        }
    }

    static void close() {
        if (projectionBuffer != null) {
            projectionBuffer.close();
            projectionBuffer = null;
        }
    }
}
