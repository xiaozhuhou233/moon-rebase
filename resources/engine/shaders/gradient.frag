#version 330 core
#include <prelude/o_gradient.glsl>
#include <mask/scissor.glsl>

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
    vec2 factor = (oPosition * oScreen - oOrigin) / oSize;
    vec4 col = mix(oFrom, oTo, oVertical == 1.0 ? factor.y : factor.x);
    fragColor = vec4(col);
}