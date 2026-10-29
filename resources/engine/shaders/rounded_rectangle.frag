#version 330 core
#include <prelude/o_roundedrect.glsl>
#include <mask/scissor.glsl>
#include <mask/gradient.glsl>
#include <mask/soft_halo.glsl>

out vec4 fragColor;

uniform Stencil stencil;

const int FLAG_TOP_LEFT = 1 << 0;
const int FLAG_TOP_RIGHT = 1 << 1;
const int FLAG_BOTTOM_LEFT = 1 << 2;
const int FLAG_BOTTOM_RIGHT = 1 << 3;

float dstfn(vec2 c, vec2 s, float r)
{
    vec2 rectangle = abs(c) - s + r;
    return min(max(rectangle.x, rectangle.y), 0.0) + length(max(rectangle, 0.0)) - r;
}

float circle(vec2 pixel)
{
    int flags = int(oFlags);
    bool tl = (flags & FLAG_TOP_LEFT) != FLAG_TOP_LEFT;
    bool tr = (flags & FLAG_TOP_RIGHT) != FLAG_TOP_RIGHT;
    bool bl = (flags & FLAG_BOTTOM_LEFT) != FLAG_BOTTOM_LEFT;
    bool br = (flags & FLAG_BOTTOM_RIGHT) != FLAG_BOTTOM_RIGHT;
    float tR = oRadius;
    bool topLeft = tl && pixel.x <= tR && pixel.y <= tR;
    bool bottomLeft = bl && pixel.x <= tR && pixel.y >= (oSize.y - tR);
    bool topRight = tr && pixel.x >= (oSize.x - tR) && pixel.y <= tR;
    bool bottomRight = br && pixel.x >= (oSize.x - tR) && pixel.y >= (oSize.y - tR);

    float l = oRadius - smoothness;
    float r = oRadius + smoothness;
    float factor = 0.0;
    float alphaValue = 1.0;
    if (topLeft) {
        factor = smoothstep(l, r, length(pixel - vec2(oRadius, oRadius)));
    } else if (bottomLeft) {
        factor = smoothstep(l, r, length(pixel - vec2(oRadius, (oSize.y - tR))));
    } else if (topRight) {
        factor = smoothstep(l, r, length(pixel - vec2((oSize.x - oRadius), oRadius)));
    } else if (bottomRight) {
        factor = smoothstep(l, r, length(pixel - vec2((oSize.x - oRadius), (oSize.y - oRadius))));
    }
    alphaValue *= 1.0 - factor;
    return alphaValue;
}

void main(void)
{
    if (halo.required == 1) {
        vec2 pixel = oPosition * halo.screenSize;
        vec2 center = halo.bounds.xy + halo.bounds.zw * 0.5;
        vec2 q = abs(pixel-center) - (halo.bounds.zw*0.5-halo.shape.x);
        float distance = length(max(q,0.0)) + min(max(q.x,q.y),0.0) - halo.shape.x;
        if(distance < 0.0) discard;
        float fade = exp(-0.5*pow(distance/max(halo.shape.y,0.01),2.0))*halo.shape.z;
        if(fade < 0.0039) discard;
        fragColor=applyGradientMaskTo(vec4(1.0,1.0,1.0,fade));
        return;
    }
    // Process stencil
    vec2 stenPos = oPosition;
    vec4 stenArea = stencil.area;
    bool stenReq = stencil.required == 1;
    bool exceeds = stenPos.x < stenArea.x || stenPos.y < stenArea.y || stenPos.x > stenArea.z || stenPos.y > stenArea.w;
    if (stenReq && exceeds)
    {
        discard;
    }

    if (oSmooth == 1.0) {
        vec2 pixel = oPosition * oScreenSize - oOrigin;
        vec2 center = oSize / 2.0;
        float sa = smoothstep(0.0, 1, dstfn(center - pixel, center - 1, oRadius));
        sa = 1.0 - smoothstep(smoothness + 0.4, 0, sa);
        vec4 c = vec4(oColor.rgb, 1 - sa);
        fragColor = applyGradientMaskTo(vec4(c.rgb, c.a * oColor.a));
    } else {
        vec2 position = oPosition * oScreenSize;
        vec2 pixel = position - oOrigin;
        float mod = 0;
        float tR = oRadius;

        int flags = int(oFlags);
        bool tl = (flags & FLAG_TOP_LEFT) == FLAG_TOP_LEFT;
        bool tr = (flags & FLAG_TOP_RIGHT) == FLAG_TOP_RIGHT;
        bool bl = (flags & FLAG_BOTTOM_LEFT) == FLAG_BOTTOM_LEFT;
        bool br = (flags & FLAG_BOTTOM_RIGHT) == FLAG_BOTTOM_RIGHT;
        bool rstw = oSize.x < oRadius / 2 || oSize.y < oRadius / 2;
        bool topLeft = pixel.x <= tR && pixel.y <= tR;
        bool bottomLeft = pixel.x <= tR && pixel.y >= (oSize.y - tR);
        bool topRight = pixel.x >= (oSize.x - tR) && pixel.y <= tR;
        bool bottomRight = pixel.x >= (oSize.x - tR) && pixel.y >= (oSize.y - tR);
        bool override = (pixel.x >= tR && pixel.x <= (oSize.x - tR))
        || (pixel.y >= tR && pixel.y <= (oSize.y - tR))
        || (topLeft && rstw) && tl
        || (topRight && rstw) && tr
        || (bottomLeft && rstw) && bl
        || (bottomRight && rstw) && br;

        if (override) {
            mod = 1;
        } else {
            mod = circle(pixel);
        }

        vec4 c = vec4(oColor.rgb, mod);
        fragColor = applyGradientMaskTo(vec4(c.rgb, c.a * oColor.a));
    }
}