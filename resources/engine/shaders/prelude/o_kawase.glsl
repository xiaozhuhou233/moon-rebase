in vec2 oPosition;
in vec2 oTexCoord;

struct Kawase
{
    sampler2D texture;
    sampler2D stencil;
    vec2 offset;
    vec2 texelSize;
    float alpha;
    float time;
    bool doStencil;
};
