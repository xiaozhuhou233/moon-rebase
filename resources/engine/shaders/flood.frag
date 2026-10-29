#version 330 core
#include <prelude/o_entity_outline.glsl>
#include <mask/scissor.glsl>

out vec4 fragColor;

uniform sampler2D tex;
uniform vec2 texelSize;
uniform vec4 u_jumpDistance;
uniform int fuck;

vec3 jump(vec3 minSeed, vec2 current, vec2 offset) {
    vec2 samplePos = current + offset;
    if (length(clamp(samplePos, 0, 1) - samplePos) > 0) { // out of bounds check
        return minSeed;
    }
    vec2 seed = texture(tex, samplePos).xy;
    vec2 cScaled = floor(current * texelSize);
    vec2 sScaled = floor(seed * texelSize);
    float dist = distance(cScaled, sScaled);
    if (dist < minSeed.z) {
        return vec3(seed.x, seed.y, dist);
    }
    return minSeed;
}

void main(void)
{
    vec2 jumpDist = round(u_jumpDistance.x) / texelSize;

    vec3 curr = vec3(1, 1, 9999999);
    curr = jump(curr, oTexCoord, jumpDist * vec2(0, 0));
    curr = jump(curr, oTexCoord, jumpDist * vec2(0, +1));
    curr = jump(curr, oTexCoord, jumpDist * vec2(+1, +1));
    curr = jump(curr, oTexCoord, jumpDist * vec2(+1, 0));
    curr = jump(curr, oTexCoord, jumpDist * vec2(+1, -1));
    curr = jump(curr, oTexCoord, jumpDist * vec2(0, -1));
    curr = jump(curr, oTexCoord, jumpDist * vec2(-1, -1));
    curr = jump(curr, oTexCoord, jumpDist * vec2(-1, 0));
    curr = jump(curr, oTexCoord, jumpDist * vec2(-1, +1));

    fragColor = vec4(curr.x, curr.y, 0, 1);
}
