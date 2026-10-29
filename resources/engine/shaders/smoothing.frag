#version 330
#include <prelude/o_smoothing.glsl>

out vec4 fragColor;

uniform sampler2D tex;
uniform vec2 texelSize;

float gauss(float x, float sigma)
{
    return 0.39894 * exp(-0.5 * x * x / (sigma * sigma)) / sigma;
}


void main() {
    vec4 blurred = vec4(0);
    for (float x = -1; x <= 1; x += 1.0) {
        for (float y = -1; y <= 1; y += 1.0) {
            vec4 tex = texture(tex, oTexCoord + texelSize * vec2(x, y));
            tex.rgb *= tex.a;
            blurred += tex;
        }
    }
    fragColor = vec4(blurred.rgb / blurred.a, blurred.a / 8.0);
}
