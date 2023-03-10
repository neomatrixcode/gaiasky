#version 330 core

// Outer color
uniform vec4 u_diffuseColor;
// Inner color
uniform vec4 u_emissiveColor;
// Camera distance encoded in u_tessQuality
uniform float u_tessQuality;
// Subgrid fading encoded in u_heightScale
uniform float u_heightScale;
// fovFactor
uniform float u_ts;

// Depth
#include shader/lib_logdepthbuff.glsl
uniform vec2 u_cameraNearFar;
uniform float u_cameraK;

// VARYINGS

// Coordinate of the texture
in vec2 v_texCoords0;
// Opacity
in float v_opacity;
// Color
in vec4 v_color;

// OUTPUT
layout (location = 0) out vec4 fragColor;
layout (location = 1) out vec4 velMap;

#define PI 3.141592
#define N 10.0
#define WIDTH 2.0

vec4 circle_rec(vec2 tc, float d, float f, float alpha, vec4 col, vec4 lcol) {
    float lw = u_ts * WIDTH;
    float factor = (1.0 - lw);

    vec2 tcp = tc * d * f;

    vec2 coord = tcp * N * 2.0;
    float dist = length(coord);

    if (dist > 40.0) {
        return vec4(0.0);
    } else if(dist < 0.3) {
        alpha *= smoothstep(0.0, 0.3, dist);
    }

    // the grid in itself
    float func = cos(PI * dist);

    // lines
    vec2 lines = smoothstep(factor, 1.0, pow(1.0 - abs(tc), vec2(3.0)));

    vec4 result = max(col * smoothstep(factor, 1.0, func), lcol * max(lines.x, lines.y));
    result.a *= alpha;
    return result;
}

vec4 circle(vec2 tc) {
    // in [-1..1]
    tc = (tc - 0.5) * 2.0;
    float alpha = v_opacity * clamp(1.0 - length(tc), 0.0, 1.0);

    float fade = pow(u_heightScale, 0.5);

    // Draw two levels
    vec4 r01 = circle_rec(tc, u_tessQuality, 10.0, alpha * fade, mix(u_emissiveColor, u_diffuseColor, u_heightScale), u_diffuseColor);
    vec4 r02 = circle_rec(tc, u_tessQuality, 1.0, alpha, u_diffuseColor, u_diffuseColor);

    return max(r01, r02);
}

vec4 square(vec2 tc) {
    vec2 coord = tc;
    coord.x *= 36.0 * 2.0;
    coord.y *= 18.0 * 2.0;
    
    // Normalize in -1..0..1
    vec2 norm = abs((tc - 0.5) * 2.0);
    
    float highlight = (smoothstep(0.001, 0.0, norm.x) + smoothstep(0.999, 1.0, norm.x) + smoothstep(0.001, 0.0, norm.y)) * 0.35;
    
    coord = cos(PI * coord);
    vec4 result = u_diffuseColor * smoothstep(0.998, 1.0, max(coord.x, coord.y));
    result = clamp(result + highlight, 0.0, 1.0);
    result.a *= pow(1.0 - abs((v_texCoords0.y * 2.0) - 1.0), 0.25) * v_opacity;
    return result;
}

void main() {
    fragColor = circle(v_texCoords0);

    gl_FragDepth = getDepthValue(u_cameraNearFar.y, u_cameraK);
    velMap = vec4(0.0, 0.0, 0.0, 1.0);
}