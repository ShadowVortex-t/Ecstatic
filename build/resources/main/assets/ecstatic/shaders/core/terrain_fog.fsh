#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <ecstatic:ecstatic_params.glsl>

layout(location = 0) in float vertexDistance;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    if (vertexColor.a == 0.0) {
        discard;
    }
    float fade = mix(1.0, ecs_fog_fade(vertexDistance, EcsFogStart, EcsFogEnd), EcsFogIntensity);
    fragColor = vec4(vertexColor.rgb, vertexColor.a * fade) * ColorModulator;
}
