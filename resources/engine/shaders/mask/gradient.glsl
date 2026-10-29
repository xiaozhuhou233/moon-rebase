struct Gradient
{
    vec4 color1;
    vec4 color2;
    float alpha;
    int factor;
    int rainbow;
    float time;
    int required;
};

uniform Gradient gradient;

#define NOISE 1 / 255.0

vec4 hue(float v) {
    return .5 + .5 * cos(gradient.factor * v + vec4(0, 23, 21, 0));
}

vec4 applyGradientMaskTo(vec4 color) {
    // Only apply gradients if required
    if (gradient.required == 0) {
        return color;
    }
    vec2 uv = oPosition;
    float texAlpha = color.a;
    vec4 col = hue(uv.x * 2 + -uv.y * 3 / 2 + gradient.time * 0.5);
    // Apply color gradient if wanted
    if (gradient.rainbow == 0) {
        float mixing = .5 + .5 * cos((gradient.time * 0.5 + uv.x - uv.y) * gradient.factor * 1.1);
        col = mix(gradient.color1, gradient.color2, mixing);
    }
    col += mix(NOISE, -NOISE, fract(sin(dot(uv.xy, vec2(12.9898, 78.233))) * 43758.5453));
    return vec4(col.rgb, texAlpha * gradient.alpha);
}