#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <ecstatic:ecstatic_params.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float vertexDistance;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a < 0.1) {
        discard;
    }
    color.rgb = mix(color.rgb, vec3(dot(color.rgb, vec3(0.299, 0.587, 0.114))), EcsSaturation);
    float fade = mix(1.0, ecs_fog_fade(vertexDistance, EcsFogStart, EcsFogEnd), EcsFogIntensity);
    fragColor = vec4(color.rgb, color.a * fade) * ColorModulator;
}
