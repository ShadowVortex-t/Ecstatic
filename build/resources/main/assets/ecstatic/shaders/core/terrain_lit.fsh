#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <ecstatic:ecstatic_params.glsl>

#ifdef TEXTURED
uniform sampler2D Sampler0;
#endif

layout(location = 0) in float vertexDistance;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
#ifdef TEXTURED
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
#else
    vec4 color = vertexColor * ColorModulator;
#endif
    fragColor = ecs_linear_fog(color, vertexDistance, EcsFogStart, EcsFogEnd, FogColor);
}
