#ifndef ECSTATIC_PARAMS_GLSL
#define ECSTATIC_PARAMS_GLSL

// Ecstatic packs its per-draw parameters into the otherwise unused TextureMat of the
// vanilla DynamicTransforms uniform block. Requires dynamictransforms.glsl to be included first.
#define EcsFogStart TextureMat[0].x
#define EcsFogEnd TextureMat[0].y
#define EcsFogIntensity TextureMat[0].z
#define EcsSaturation TextureMat[0].w
#define EcsSunDir TextureMat[1].xyz
#define EcsGameTime TextureMat[1].w

// smooth fog toward the fog colour (replacement for the old linear_fog)
vec4 ecs_linear_fog(vec4 inColor, float dist, float fogStart, float fogEnd, vec4 fogColor) {
    if (dist <= fogStart) {
        return inColor;
    }
    float fogValue = dist < fogEnd ? smoothstep(fogStart, fogEnd, dist) : 1.0;
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue), inColor.a);
}

// alpha multiplier that fades to 0 between fogStart and fogEnd (replacement for linear_fog_fade)
float ecs_fog_fade(float dist, float fogStart, float fogEnd) {
    if (dist <= fogStart) {
        return 1.0;
    } else if (dist >= fogEnd) {
        return 0.0;
    }
    return smoothstep(fogEnd, fogStart, dist);
}

#endif
