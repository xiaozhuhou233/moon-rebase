#version 330 core
#include <prelude/o_texture.glsl>
#include <mask/scissor.glsl>
#include <mask/gradient.glsl>

out vec4 fragColor;

uniform sampler2D tex;

uniform Stencil stencil;

void main(void)
{
    // Process stencil
    vec2 stenPos = oPosition;
    vec4 stenArea = stencil.area;
    bool stenReq = stencil.required == 1;
    bool exceeds = stenPos.x < stenArea.x || stenPos.y < stenArea.y || stenPos.x > stenArea.z || stenPos.y > stenArea.w;
    if (stenReq && exceeds)
    {
        discard;
    }
    vec4 color = oColor;
    vec4 textureColor = texture(tex, oTexCoord);
    fragColor = applyGradientMaskTo(vec4(color.rgb, color.a * textureColor.a));
}