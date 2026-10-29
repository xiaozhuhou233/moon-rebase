#version 330 core
#include <prelude/o_msdf.glsl>
#include <mask/scissor.glsl>
#include <mask/gradient.glsl>

out vec4 fragColor;

uniform sampler2D tex;

uniform Stencil stencil;

uniform float fontWeight;
uniform vec2 textureSize;
uniform float smoothness;

const float minimumOpacity = 0.08;

float median(float red, float green, float blue) {
    return max(min(red, green), min(max(red, green), blue));
}

void main() {
    // Process stencil
    vec2 stenPos = oPosition;
    vec4 stenArea = stencil.area;
    bool stenReq = stencil.required == 1;
    bool exceeds = stenPos.x < stenArea.x || stenPos.y < stenArea.y || stenPos.x > stenArea.z || stenPos.y > stenArea.w;
    if (stenReq && exceeds)
    {
        discard;
    }

    if (oTexCoord.x == -1) {
        fragColor = oColor;
        return;
    }
    vec3 textureColor = texture(tex, oTexCoord).rgb;
    ivec2 fTextureSize = ivec2(textureSize);
    float dx = dFdx(oTexCoord.x) * fTextureSize.x;
    float dy = dFdy(oTexCoord.y) * fTextureSize.y;
    float toPixels = 22.0 * inversesqrt(dx * dx + dy * dy);
    float weight = fontWeight + oFontWeightAdd;
    float median = median(textureColor.r, textureColor.g, textureColor.b) - 0.5 + weight;
    float opacity = smoothstep(-oFontSmooth, oFontSmooth, median * toPixels);
    if (opacity < minimumOpacity) {
        discard;
    }
    vec4 finalColor = vec4(oColor.rgb, oColor.a * opacity);
    fragColor = applyGradientMaskTo(finalColor);
}