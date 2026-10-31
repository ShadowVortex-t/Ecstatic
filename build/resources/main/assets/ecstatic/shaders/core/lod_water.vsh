#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out float vertexDistance;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) out vec2 texCoord0;
layout(location = 3) out vec3 eyePos;

void main() {
    vec4 eyePosition = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * eyePosition;
    vertexDistance = length(eyePosition.xyz);
    vertexColor = Color;
    texCoord0 = UV0;
    eyePos = eyePosition.xyz;
}
