#version 330 core
#include <prelude/matrix.glsl>
#include <prelude/roundedrect.glsl>

uniform Matrix matrix;

void main(void)
{
    mat4 mvp = matrix.projection * matrix.modelview;
    gl_Position = mvp * vec4(position, 0.0, 1.0);
    // Define vertex data
    oPosition = position;
    oSize = size;
    oScreenSize = screenSize;
    oOrigin = origin;
    oRadius = radius;
    oColor = color;
    oFlags = flags;
    oSmooth = smoothing;
}