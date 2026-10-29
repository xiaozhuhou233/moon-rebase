#version 330 core
#include <prelude/o_world.glsl>

out vec4 fragColor;

void main(void)
{
    fragColor = vec4(oColor);
}