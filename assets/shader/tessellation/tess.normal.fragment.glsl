#version 410 core

////////////////////////////////////////////////////////////////////////////////////
////////// NORMAL ATTRIBUTE - FRAGMENT
///////////////////////////////////////////////////////////////////////////////////
vec3 g_normal = vec3(0.0, 0.0, 1.0);
#define pullNormal() g_normal = o_data.normal

////////////////////////////////////////////////////////////////////////////////////
////////// BINORMAL ATTRIBUTE - FRAGMENT
///////////////////////////////////////////////////////////////////////////////////
vec3 g_binormal = vec3(0.0, 0.0, 1.0);

////////////////////////////////////////////////////////////////////////////////////
////////// TANGENT ATTRIBUTE - FRAGMENT
///////////////////////////////////////////////////////////////////////////////////
vec3 g_tangent = vec3(1.0, 0.0, 0.0);

// Uniforms which are always available
uniform vec2 u_cameraNearFar;
uniform float u_cameraK;

#ifdef diffuseColorFlag
uniform vec4 u_diffuseColor;
#endif

#ifdef diffuseTextureFlag
uniform sampler2D u_diffuseTexture;
#endif

#ifdef diffuseCubemapFlag
uniform samplerCube u_diffuseCubemap;
#endif

#ifdef specularColorFlag
uniform vec4 u_specularColor;
#endif

#ifdef specularTextureFlag
uniform sampler2D u_specularTexture;
#endif

#ifdef specularCubemapFlag
uniform samplerCube u_specularCubemap;
#endif

#ifdef emissiveColorFlag
uniform vec4 u_emissiveColor;
#endif

#ifdef emissiveTextureFlag
uniform sampler2D u_emissiveTexture;
#include shader/lib_luma.glsl
#endif

#ifdef emissiveCubemapFlag
uniform samplerCube u_emissiveCubemap;
#endif

#ifdef metallicColorFlag
uniform vec4 u_metallicColor;
#endif

#ifdef metallicTextureFlag
uniform sampler2D u_metallicTexture;
#endif

#ifdef metallicCubemapFlag
uniform samplerCube u_metallicCubemap;
#endif

#ifdef roughnessTextureFlag
uniform sampler2D u_roughnessTexture;
#endif

#ifdef roughnessCubemapFlag
uniform samplerCube u_roughnessCubemap;
#endif

#ifdef reflectionCubemapFlag
uniform samplerCube u_reflectionCubemap;
#endif

#ifdef svtCacheTextureFlag
uniform sampler2D u_svtCacheTexture;
#endif

#ifdef svtIndirectionDiffuseTextureFlag
uniform sampler2D u_svtIndirectionDiffuseTexture;
#endif

#ifdef svtIndirectionSpecularTextureFlag
uniform sampler2D u_svtIndirectionSpecularTexture;
#endif

#ifdef svtIndirectionEmissiveTextureFlag
uniform sampler2D u_svtIndirectionEmissiveTexture;
#endif

#ifdef svtIndirectionMetallicTextureFlag
uniform sampler2D u_svtIndirectionMetallicTexture;
#endif

#ifdef shininessFlag
uniform float u_shininess;
#endif

//////////////////////////////////////////////////////
////// SHADOW MAPPING
//////////////////////////////////////////////////////
#ifdef shadowMapFlag
#define bias 0.030
uniform sampler2D u_shadowTexture;
uniform float u_shadowPCFOffset;

float getShadowness(vec2 uv, vec2 offset, float compare){
    const vec4 bitShifts = vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 160581375.0);
    return step(compare - bias, dot(texture(u_shadowTexture, uv + offset), bitShifts)); //+(1.0/255.0));
}


float textureShadowLerp(vec2 size, vec2 uv, float compare){
    vec2 texelSize = vec2(1.0) / size;
    vec2 f = fract(uv * size + 0.5);
    vec2 centroidUV = floor(uv * size + 0.5) / size;

    float lb = getShadowness(centroidUV, texelSize * vec2(0.0, 0.0), compare);
    float lt = getShadowness(centroidUV, texelSize * vec2(0.0, 1.0), compare);
    float rb = getShadowness(centroidUV, texelSize * vec2(1.0, 0.0), compare);
    float rt = getShadowness(centroidUV, texelSize * vec2(1.0, 1.0), compare);
    float a = mix(lb, lt, f.y);
    float b = mix(rb, rt, f.y);
    float c = mix(a, b, f.x);
    return c;
}

float getShadow(vec3 shadowMapUv) {
    // Complex lookup: PCF + interpolation (see http://codeflow.org/entries/2013/feb/15/soft-shadow-mapping/)
    vec2 size = vec2(1.0 / (2.0 * u_shadowPCFOffset));
    float result = 0.0;
    for(int x=-2; x<=2; x++) {
        for(int y=-2; y<=2; y++) {
            vec2 offset = vec2(float(x), float(y)) / size;
            result += textureShadowLerp(size, shadowMapUv.xy + offset, shadowMapUv.z);
        }
    }
    return result / 25.0;

    // Simple lookup
    //return getShadowness(o_data.shadowMapUv.xy, vec2(0.0), o_data.shadowMapUv.z);
}
#endif // shadowMapFlag
//////////////////////////////////////////////////////
//////////////////////////////////////////////////////

//////////////////////////////////////////////////////
////// CUBEMAPS
//////////////////////////////////////////////////////
#ifdef cubemapFlag
    #include shader/lib_cubemap.glsl
#endif // cubemapFlag
//////////////////////////////////////////////////////
//////////////////////////////////////////////////////

//////////////////////////////////////////////////////
////// SVT
//////////////////////////////////////////////////////
#ifdef svtFlag
#include shader/lib_svt.glsl
#endif // svtFlag
//////////////////////////////////////////////////////
//////////////////////////////////////////////////////

// COLOR DIFFUSE
#if defined(diffuseTextureFlag) && defined(diffuseColorFlag)
    #define fetchColorDiffuseTD(texCoord, defaultValue) texture(u_diffuseTexture, texCoord) * u_diffuseColor
#elif defined(diffuseTextureFlag)
    #define fetchColorDiffuseTD(texCoord, defaultValue) texture(u_diffuseTexture, texCoord)
#elif defined(diffuseColorFlag)
    #define fetchColorDiffuseTD(texCoord, defaultValue) u_diffuseColor
#else
    #define fetchColorDiffuseTD(texCoord, defaultValue) defaultValue
#endif // diffuse

#if defined(svtIndirectionDiffuseTextureFlag)
    #define fetchColorDiffuse(baseColor, texCoord, defaultValue) baseColor * texture(u_svtCacheTexture, svtTexCoords(u_svtIndirectionDiffuseTexture, texCoord))
#elif defined(diffuseCubemapFlag)
    #define fetchColorDiffuse(baseColor, texCoord, defaultValue) baseColor * texture(u_diffuseCubemap, UVtoXYZ(texCoord))
#elif defined(diffuseTextureFlag) || defined(diffuseColorFlag)
    #define fetchColorDiffuse(baseColor, texCoord, defaultValue) baseColor * fetchColorDiffuseTD(texCoord, defaultValue)
#else
    #define fetchColorDiffuse(baseColor, texCoord, defaultValue) baseColor
#endif // diffuse

// COLOR EMISSIVE
#if defined(emissiveTextureFlag) && defined(emissiveColorFlag)
    #define fetchColorEmissiveTD(tex, texCoord) texture(tex, texCoord) + u_emissiveColor
#elif defined(emissiveTextureFlag)
    #define fetchColorEmissiveTD(tex, texCoord) texture(tex, texCoord)
#elif defined(emissiveColorFlag)
    #define fetchColorEmissiveTD(tex, texCoord) u_emissiveColor
#endif // emissive

#if defined(svtIndirectionEmissiveTextureFlag)
    #define fetchColorEmissive(texCoord) texture(u_svtCacheTexture, svtTexCoords(u_svtIndirectionEmissiveTexture, texCoord))
#elif defined(emissiveCubemapFlag)
    #define fetchColorEmissive(texCoord) texture(u_emissiveCubemap, UVtoXYZ(texCoord))
#elif defined(emissiveTextureFlag) || defined(emissiveColorFlag)
    #define fetchColorEmissive(texCoord) fetchColorEmissiveTD(u_emissiveTexture, texCoord)
#else
    #define fetchColorEmissive(texCoord) vec4(0.0, 0.0, 0.0, 0.0)
#endif // emissive

// COLOR SPECULAR
#if defined(svtIndirectionSpecularTextureFlag)
    #define fetchColorSpecular(texCoord, defaultValue) texture(u_svtCacheTexture, svtTexCoords(u_svtIndirectionSpecularTexture, texCoord))
#elif defined(specularCubemapFlag)
    #define fetchColorSpecular(texCoord, defaultValue) texture(u_specularCubemap, UVtoXYZ(texCoord))
#elif defined(specularTextureFlag) && defined(specularColorFlag)
    #define fetchColorSpecular(texCoord, defaultValue) texture(u_specularTexture, texCoord).rgb * u_specularColor.rgb
#elif defined(specularTextureFlag)
    #define fetchColorSpecular(texCoord, defaultValue) texture(u_specularTexture, texCoord).rgb
#elif defined(specularColorFlag)
    #define fetchColorSpecular(texCoord, defaultValue) u_specularColor.rgb
#else
    #define fetchColorSpecular(texCoord, defaultValue) defaultValue
#endif // specular

// COLOR METALLIC
#ifdef svtIndirectionMetallicTextureFlag
    #define fetchColorMetallic(texCoord) texture(u_svtCacheTexture, svtTexCoords(u_svtIndirectionMetallicTexture, texCoord))
#elif metallicCubemapFlag
    #define fetchColorMetallic(texCoord) texture(u_metallicCubemap, UVtoXYZ(texCoord))
#elif defined(metallicTextureFlag)
    #define fetchColorMetallic(texCoord) texture(u_metallicTexture, texCoord)
#elif defined(metallicColorFlag)
    #define fetchColorMetallic(texCoord) u_metallicColor
#endif // metallic

// COLOR ROUGHNESS
#if defined(svtIndirectionRoughnessTextureFlag)
    #define fetchColorRoughness(texCoord) texture(u_svtCacheTexture, svtTexCoords(u_svtIndirectionRoughnessTexture, texCoord))
#elif defined(roughnessCubemapFlag)
    #define fetchColorRoughness(texCoord) texture(u_roughnessCubemap, UVtoXYZ(texCoord))
#elif defined(roughnessTextureFlag)
    #define fetchColorRoughness(texCoord) texture(u_roughnessTexture, texCoord)
#endif // roughness

#if defined(numDirectionalLights) && (numDirectionalLights > 0)
#define directionalLightsFlag
#endif // numDirectionalLights

#ifdef directionalLightsFlag
struct DirectionalLight {
    vec3 color;
    vec3 direction;
};
#endif // directionalLightsFlag

// INPUT
struct VertexData {
    vec2 texCoords;
    vec3 normal;
    #ifdef directionalLightsFlag
    DirectionalLight directionalLights[numDirectionalLights];
    #endif // directionalLightsFlag
    vec3 viewDir;
    vec3 ambientLight;
    float opacity;
    vec4 color;
    #ifdef shadowMapFlag
    vec3 shadowMapUv;
    #endif // shadowMapFlag
    vec3 fragPosWorld;
    #ifdef reflectionCubemapFlag
    vec3 reflect;
    #endif // reflectionCubemapFlag
};
in VertexData o_data;

#ifdef atmosphereGround
in vec4 o_atmosphereColor;
in float o_fadeFactor;
#endif // atmosphereGround

in vec3 o_normalTan;

// OUTPUT
layout (location = 0) out vec4 fragColor;

#ifdef ssrFlag
#include shader/lib_ssr.frag.glsl
#endif // ssrFlag

#define saturate(x) clamp(x, 0.0, 1.0)

#include shader/lib_atmfog.glsl
#include shader/lib_logdepthbuff.glsl

// http://www.thetenthplanet.de/archives/1180
mat3 cotangentFrame(vec3 N, vec3 p, vec2 uv){
    // get edge vectors of the pixel triangle
    vec3 dp1 = dFdx( p );
    vec3 dp2 = dFdy( p );
    vec2 duv1 = dFdx( uv );
    vec2 duv2 = dFdy( uv );

    // solve the linear system
    vec3 dp2perp = cross( dp2, N );
    vec3 dp1perp = cross( N, dp1 );
    vec3 T = dp2perp * duv1.x + dp1perp * duv2.x;
    vec3 B = dp2perp * duv1.y + dp1perp * duv2.y;

    // construct a scale-invariant frame
    float invmax = inversesqrt( max( dot(T,T), dot(B,B) ) );
    return mat3( T * invmax, B * invmax, N );
}

#ifdef velocityBufferFlag
#include shader/lib_velbuffer.frag.glsl
#endif // velocityBufferFlag

#ifdef ssrFlag
#include shader/lib_pack.glsl
#endif // ssrFlag

// MAIN
void main() {
    vec2 texCoords = o_data.texCoords;

    vec4 diffuse = fetchColorDiffuse(o_data.color, texCoords, vec4(1.0, 1.0, 1.0, 1.0));
    vec4 emissive = fetchColorEmissive(texCoords);
    vec3 specular = fetchColorSpecular(texCoords, vec3(0.0, 0.0, 0.0));
    vec3 ambient = o_data.ambientLight;
    #ifdef atmosphereGround
    vec3 night = emissive.rgb;
    emissive = vec4(0.0);
    #else
    vec3 night = vec3(0.0);
    #endif

    // Alpha value from textures
    float texAlpha = 1.0;
    #if defined(diffuseTextureFlag) || defined(diffuseCubemapFlag)
    texAlpha = diffuse.a;
    #elif defined(emissiveTextureFlag)
    texAlpha = luma(emissive.rgb);
    #endif

    vec4 normalVector = vec4(0.0, 0.0, 0.0, 1.0);
    vec3 N = o_normalTan;
    #if defined(normalTextureFlag) || defined(normalCubemapFlag)
        #ifdef metallicFlag
            // Perturb the normal to get reflect direction
            pullNormal();
            mat3 TBN = cotangentFrame(g_normal, -o_data.viewDir, texCoords);
            normalVector.xyz = TBN * N;
            vec3 reflectDir = normalize(reflect(o_data.fragPosWorld, normalVector.xyz));
        #endif // metallicFlag
    #else
        normalVector.xyz = o_data.normal;
        #ifdef reflectionCubemapFlag
            vec3 reflectDir = normalize(o_data.reflect);
        #endif // reflectionCubemapFlag
    #endif // normalTextureFlag

    // Shadow
    #ifdef shadowMapFlag
    float shdw = clamp(getShadow(o_data.shadowMapUv), 0.0, 1.0);
    #else
    float shdw = 1.0;
    #endif

    // Reflection
    vec3 reflectionColor = vec3(0.0);
    // Reflection mask
    #ifdef ssrFlag
        reflectionMask = vec4(0.0, 0.0, 0.0, 1.0);
    #endif // ssrFlag

    #ifdef metallicFlag
        float roughness = 0.0;
        #if defined(roughnessTextureFlag) || defined(roughnessCubemapFlag)
            roughness = fetchColorRoughness(texCoords).x;
        #elif defined(shininessFlag)
            roughness = 1.0 - u_shininess;
        #endif // roughnessTextureFlag, shininessFlag

        #ifdef reflectionCubemapFlag
            reflectionColor = texture(u_reflectionCubemap, vec3(-reflectDir.x, reflectDir.y, reflectDir.z), roughness * 7.0).rgb;
        #endif // reflectionCubemapFlag

        vec3 metallicColor = fetchColorMetallic(texCoords).rgb;
        reflectionColor = reflectionColor * metallicColor;
        #ifdef ssrFlag
            vec3 rmc = diffuse.rgb * metallicColor;
            reflectionMask = vec4(rmc.r, pack2(rmc.gb), roughness, 1.0);
            reflectionColor *= 0.0;
        #else
            reflectionColor += reflectionColor * diffuse.rgb;
        #endif // ssrFlag
    #endif // metallicFlag

    vec3 shadowColor = vec3(0.0);
    vec3 diffuseColor = vec3(0.0);
    vec3 specularColor = vec3(0.0);
    float selfShadow = 1.0;
    vec3 fog = vec3(0.0);

    float NL0;
    vec3 L0;

    // Loop for directional light contributitons
    #ifdef directionalLightsFlag
    vec3 V = o_data.viewDir;
    // Loop for directional light contributitons
    for (int i = 0; i < numDirectionalLights; i++) {
        vec3 col = o_data.directionalLights[i].color;
        // Skip non-lights
        if (col.r == 0.0 && col.g == 0.0 && col.b == 0.0) {
            continue;
        }
        // see http://http.developer.nvidia.com/CgTutorial/cg_tutorial_chapter05.html
        vec3 L = o_data.directionalLights[i].direction;
        vec3 H = normalize(L - V);
        float NL = max(0.0, dot(N, L));
        float NH = max(0.0, dot(N, H));
        if (i == 0){
            NL0 = NL;
            L0 = L;
        }

        selfShadow *= saturate(4.0 * NL);

        specularColor += specular * min(1.0, pow(NH, 40.0));
        shadowColor += col * night * max(0.0, 0.5 - NL) * shdw;
        diffuseColor = saturate(diffuseColor + col * NL * shdw + ambient * (1.0 - NL));
    }
    diffuseColor *= diffuse.rgb;
    #endif // directionalLightsFlag

    // Final color equation
    fragColor = vec4(diffuseColor + shadowColor + emissive.rgb + reflectionColor, texAlpha * o_data.opacity);
    fragColor.rgb += selfShadow * specularColor;

    #ifdef atmosphereGround
    #define exposure 4.0
        fragColor.rgb += (vec3(1.0) - exp(o_atmosphereColor.rgb * -exposure)) * o_atmosphereColor.a * shdw * o_fadeFactor;
        fragColor.rgb = applyFog(fragColor.rgb, o_data.viewDir, L0 * -1.0, NL0);
    #endif // atmosphereGround

    if (fragColor.a <= 0.0) {
        discard;
    }

    #ifdef ssrFlag
        normalBuffer = vec4(normalVector.xyz, 1.0);
    #endif // ssrFlag

    // Logarithmic depth buffer
    gl_FragDepth = getDepthValue(u_cameraNearFar.y, u_cameraK);

    #ifdef velocityBufferFlag
    velocityBuffer();
    #endif // velocityBufferFlag
}
