#version 330 core
#include <prelude/o_kawase.glsl>

out vec4 fragColor;

uniform Kawase kawase;

#define halfPixel (kawase.texelSize * 0.5)

void main(){
    vec2 offset = kawase.offset;
    float alpha = kawase.alpha;
    vec4 color = texture2D(kawase.texture, oTexCoord) * 4.0;
    color += texture2D(kawase.texture, oTexCoord + halfPixel * offset);
    color += texture2D(kawase.texture, oTexCoord - halfPixel * offset);
    color += texture2D(kawase.texture, oTexCoord + vec2(halfPixel.x, -halfPixel.y) * offset);
    color += texture2D(kawase.texture, oTexCoord - vec2(halfPixel.x, -halfPixel.y) * offset);
    fragColor = vec4(color.rgb / 8.0, alpha > 0 ? alpha : color.a / 8.0);
}
