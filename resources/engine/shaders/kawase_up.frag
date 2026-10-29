#version 330 core

#include <prelude/o_kawase.glsl>

out vec4 fragColor;

uniform Kawase kawase;

#define halfPixel (kawase.texelSize * 0.5)

void main(){
    // Apply stencil
    float alpha = kawase.alpha;
    if (kawase.doStencil) {
        vec4 sten = texture2D(kawase.stencil, oTexCoord);
        alpha = sten.a;
    }
    vec2 offset = kawase.offset;
    vec4 color = texture2D(kawase.texture, oTexCoord + vec2(-halfPixel.x * 2.0, 0.0) * offset);
    color += texture2D(kawase.texture, oTexCoord + vec2(-halfPixel.x, halfPixel.y) * offset) * 2.0;
    color += texture2D(kawase.texture, oTexCoord + vec2(0.0, halfPixel.y * 2.0) * offset);
    color += texture2D(kawase.texture, oTexCoord + vec2(halfPixel.x, halfPixel.y) * offset) * 2.0;
    color += texture2D(kawase.texture, oTexCoord + vec2(halfPixel.x * 2.0, 0.0) * offset);
    color += texture2D(kawase.texture, oTexCoord + vec2(halfPixel.x, -halfPixel.y) * offset) * 2.0;
    color += texture2D(kawase.texture, oTexCoord + vec2(0.0, -halfPixel.y * 2.0) * offset);
    color += texture2D(kawase.texture, oTexCoord + vec2(-halfPixel.x, -halfPixel.y) * offset) * 2.0;
    fragColor = vec4(color.rgb / 12.0, alpha);
}
