#version 330 core
#include <prelude/o_entity_outline.glsl>
#include <mask/scissor.glsl>

out vec4 fragColor;
uniform sampler2D tex;

void main(void)
{
    vec4 texCol = texture(tex, oTexCoord);
    if (texCol.a <= 0) {
        fragColor = vec4(-1, -1, -1, -1);
    } else {
        fragColor = vec4(oTexCoord.x, oTexCoord.y, 0, 1);
    }
}