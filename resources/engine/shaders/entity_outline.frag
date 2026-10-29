#version 330 core
#include <prelude/o_entity_outline.glsl>
#include <mask/scissor.glsl>

out vec4 fragColor;

uniform sampler2D tex;
uniform sampler2D stencilTex;
uniform float[100] bezierSamples;

uniform Stencil stencil;
uniform Outline outline;

uniform float brightness;
uniform float contrast;
uniform float saturation;

mat4 brightnessMat(float brightness)
{
    return mat4(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, brightness, brightness, brightness, 1);
}

mat4 contrastMat(float contrast)
{
    float t = (1.0 - contrast) / 2.0;
    return mat4(contrast, 0, 0, 0, 0, contrast, 0, 0, 0, 0, contrast, 0, t, t, t, 1);
}

mat4 saturationMat(float saturation)
{
    vec3 luminance = vec3(0.3086, 0.6094, 0.0820);
    float oneMinusSat = 1.0 - saturation;
    vec3 red = vec3(luminance.x * oneMinusSat);
    red += vec3(saturation, 0, 0);
    vec3 green = vec3(luminance.y * oneMinusSat);
    green += vec3(0, saturation, 0);
    vec3 blue = vec3(luminance.z * oneMinusSat);
    blue += vec3(0, 0, saturation);
    return mat4(red, 0, green, 0, blue, 0, 0, 0, 0, 1);
}

vec4 outlineFunc(vec2 v_texcoord0, float maxOutlineWidth)
{
    vec2 closestSeed = texture(stencilTex, v_texcoord0).xy;
    vec2 distance = closestSeed - v_texcoord0;
    float dist = length(closestSeed / outline.texelSize - v_texcoord0 / outline.texelSize);
    float texelSize = outline.texelSize.x;
    float targetWidth = maxOutlineWidth;
    if (closestSeed.x > 0 && closestSeed.y > 0) {
        float progress = dist / targetWidth;
        int progressRound = clamp(int(progress * 99), 0, 99);
        float evaluated = bezierSamples[progressRound];
        float maxAlpha = (targetWidth - dist + 1.0);
        float result = evaluated * min(maxAlpha, 1);
        // Save performance by only executing some texture queries if required
        if (result > 0) {
            vec4 color = texture(tex, v_texcoord0);
            if (color.a > 0) {
                return vec4(0.0);
            }
            vec4 stencilColor = texture(tex, closestSeed);
            return vec4(stencilColor.rgb, evaluated * min(maxAlpha, 1));
        }
    }
    return vec4(0.0);
}


void main(void)
{
    fragColor = brightnessMat(brightness) * contrastMat(contrast) * saturationMat(saturation) * outlineFunc(oTexCoord, outline.radius);
}