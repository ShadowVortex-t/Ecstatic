#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <ecstatic:ecstatic_params.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float vertexDistance;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

#define LEAF_ALPHA_FLOOR 0.85

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    color.a = max(color.a, LEAF_ALPHA_FLOOR);
    vec4 fogged = ecs_linear_fog(color, vertexDistance, EcsFogStart, EcsFogEnd, FogColor);
    fragColor = mix(color, fogged, EcsFogIntensity) * ColorModulator;
}
