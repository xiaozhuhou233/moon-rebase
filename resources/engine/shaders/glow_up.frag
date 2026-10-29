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
        if (sten.a > 0) {
            discard;
        }
    }
    vec2 offset = kawase.offset;
    vec4 sum = texture2D(kawase.texture, oTexCoord + vec2(-halfPixel.x * 2.0, 0.0) * offset);
    sum.rgb *= sum.a;
    vec4 tex1 = texture2D(kawase.texture, oTexCoord + vec2(-halfPixel.x, halfPixel.y) * offset);
    tex1.rgb *= tex1.a;
    sum += tex1 * 2.0;
    vec4 tex2 = texture2D(kawase.texture, oTexCoord + vec2(0.0, halfPixel.y * 2.0) * offset);
    tex2.rgb *= tex2.a;
    sum += tex2;
    vec4 tex3 = texture2D(kawase.texture, oTexCoord + vec2(halfPixel.x, halfPixel.y) * offset);
    tex3.rgb *= tex3.a;
    sum += tex3 * 2.0;
    vec4 tex4 = texture2D(kawase.texture, oTexCoord + vec2(halfPixel.x * 2.0, 0.0) * offset);
    tex4.rgb *= tex4.a;
    sum += tex4;
    vec4 tex5 = texture2D(kawase.texture, oTexCoord + vec2(halfPixel.x, -halfPixel.y) * offset);
    tex5.rgb *= tex5.a;
    sum += tex5 * 2.0;
    vec4 tex6 = texture2D(kawase.texture, oTexCoord + vec2(0.0, -halfPixel.y * 2.0) * offset);
    tex6.rgb *= tex6.a;
    sum += tex6;
    vec4 tex7 = texture2D(kawase.texture, oTexCoord + vec2(-halfPixel.x, -halfPixel.y) * offset);
    tex7.rgb *= tex7.a;
    sum += tex7 * 2.0;
    vec4 result = sum / 12.0;
    fragColor = vec4(result.rgb / result.a, result.a * alpha);
}
