package com.angryalchemist.ecstatic.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;

/** A static, non-indexed triangle mesh living in a GPU vertex buffer. */
final class GpuMesh implements AutoCloseable {
    private final GpuBuffer buffer;
    private final int vertexCount;

    private GpuMesh(GpuBuffer buffer, int vertexCount) {
        this.buffer = buffer;
        this.vertexCount = vertexCount;
    }

    GpuBuffer buffer() {
        return this.buffer;
    }

    int vertexCount() {
        return this.vertexCount;
    }

    /** Starts a triangle-list builder with room for {@code vertexCount} vertices. */
    static Builder begin(VertexFormat format, int vertexCount) {
        ByteBufferBuilder bytes = new ByteBufferBuilder(Math.max(256, vertexCount * format.getVertexSize()));
        return new Builder(bytes, new BufferBuilder(bytes, PrimitiveTopology.TRIANGLES, format));
    }

    @Override
    public void close() {
        this.buffer.close();
    }

    static final class Builder implements AutoCloseable {
        private final ByteBufferBuilder bytes;
        private final BufferBuilder builder;

        private Builder(ByteBufferBuilder bytes, BufferBuilder builder) {
            this.bytes = bytes;
            this.builder = builder;
        }

        BufferBuilder vertices() {
            return this.builder;
        }

        /** Finishes the mesh and uploads it, or returns null if no vertices were written. */
        GpuMesh upload(String label) {
            try (MeshData mesh = this.builder.build()) {
                if (mesh == null) {
                    return null;
                }

                GpuBuffer gpuBuffer = RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                return new GpuMesh(gpuBuffer, mesh.drawState().vertexCount());
            }
        }

        @Override
        public void close() {
            this.bytes.close();
        }
    }
}
