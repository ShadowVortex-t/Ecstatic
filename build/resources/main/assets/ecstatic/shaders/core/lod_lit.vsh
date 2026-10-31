#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;
layout(location = 4) in vec4 Normal;

layout(location = 0) out float vertexDistance;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) out vec2 texCoord0;

void main() {
    vec4 eyePosition = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * eyePosition;
    vertexDistance = length(eyePosition.xyz);
    // The lit format always carries full-bright light coordinates (0xF000F0), so the lightmap is skipped.
    vertexColor = Color;
    texCoord0 = UV0;
}
