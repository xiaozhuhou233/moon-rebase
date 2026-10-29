#version 330 core
#include <prelude/o_kawase.glsl>

out vec4 fragColor;

uniform Kawase kawase;

#define halfPixel (kawase.texelSize * 0.5)

void main(){
    vec2 offset = kawase.offset;
    vec4 sum = texture2D(kawase.texture, oTexCoord);
    sum.rgb *= sum.a;
    sum *= 4.0;
    vec4 tex1 = texture2D(kawase.texture, oTexCoord - halfPixel.xy * offset);
    tex1.rgb *= tex1.a;
    sum += tex1;
    vec4 tex2 = texture2D(kawase.texture, oTexCoord + halfPixel.xy * offset);
    tex2.rgb *= tex2.a;
    sum += tex2;
    vec4 tex3 = texture2D(kawase.texture, oTexCoord + (vec2(halfPixel.x, -halfPixel.y) * offset));
    tex3.rgb *= tex3.a;
    sum += tex3;
    vec4 tex4 = texture2D(kawase.texture, oTexCoord - vec2(halfPixel.x, -halfPixel.y) * offset);
    tex4.rgb *= tex4.a;
    sum += tex4;
    vec4 result = sum / 8.0;
    fragColor = vec4(result.a > 0.00001 ? result.rgb / result.a : vec3(0.0), result.a);
}
