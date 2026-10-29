#version 330 core
#include <prelude/o_roundedtexture.glsl>
#include <mask/scissor.glsl>
#include <mask/gradient.glsl>

out vec4 fragColor;

uniform sampler2D tex;
uniform Stencil stencil;

float dstfn(vec2 c, vec2 s, float r)
{
    return length(max(abs(c) - s + r, 0.0)) - r;
}

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

    vec2 pixel = oPosition * oScreenSize - oOrigin;
    vec2 center = oSize / 2.0;
    float sa = smoothstep(0.0, 1, dstfn(center - pixel, center - 1, oRadius));
    sa = 1.0 - smoothstep(smoothness + 0.4, 0, sa);
    vec4 tex = texture(tex, oTexCoord);
    fragColor = vec4(tex.rgb * oColor.rgb, vec4(oColor.rgb, 1 - sa).a * oColor.a * tex.a);
}