#version 330 core
#include <prelude/o_rectangle.glsl>
#include <mask/scissor.glsl>
#include <mask/gradient.glsl>

out vec4 fragColor;

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
    fragColor = applyGradientMaskTo(vec4(oColor));
}