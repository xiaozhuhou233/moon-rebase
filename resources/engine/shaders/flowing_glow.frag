#version 330 compatibility
uniform sampler2D glowTexture;
uniform vec3 colorOne;
uniform vec3 colorTwo;
uniform vec2 screenSize;
uniform float time;
in vec2 uv;
out vec4 fragColor;
void main() {
    vec4 coverage = texture(glowTexture, uv);
    vec2 p = vec2(uv.x, 1.0-uv.y) * screenSize;
    float mixing = 0.5 + 0.5 * sin((p.x + p.y * 0.35) / 95.0 - time * 1.4);
    vec3 flowing = mix(colorOne, colorTwo, mixing);
    float chroma = max(coverage.r, max(coverage.g, coverage.b)) - min(coverage.r, min(coverage.g, coverage.b));
    vec3 tint = chroma > 0.035 ? coverage.rgb : flowing;
    fragColor = vec4(tint, coverage.a);
}
