// The camera look over the whole picture: lens colour fringing toward the edges, film grain,
// faint scanlines and a vignette. One ColorRect covering the screen uses this (see main.gd).
shader_type canvas_item;

uniform sampler2D screen : hint_screen_texture, filter_linear;
uniform float fringe = 0.0035;      // how far red and blue split at the edge
uniform float grain = 0.055;
uniform float vignette = 0.85;
uniform float scanlines = 0.05;
uniform float glitch = 0.0;         // 0..1: tears the picture sideways in bands (set briefly by the game)

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void fragment() {
    vec2 uv = SCREEN_UV;
    if (glitch > 0.0) {
        float band = floor(uv.y * 24.0 + TIME * 37.0);
        float tear = step(1.0 - glitch * 0.5, hash(vec2(band, floor(TIME * 30.0))));
        uv.x += tear * (hash(vec2(band, 3.0)) - 0.5) * 0.12 * glitch;
    }
    vec2 from_centre = uv - 0.5;
    float d = dot(from_centre, from_centre);
    vec2 shift = from_centre * fringe * (0.4 + d * 4.0);
    vec3 col;
    col.r = texture(screen, uv - shift).r;
    col.g = texture(screen, uv).g;
    col.b = texture(screen, uv + shift).b;
    col += (hash(uv * vec2(1920.0, 1080.0) + fract(TIME) * 100.0) - 0.5) * grain;
    col *= 1.0 - scanlines * step(0.5, fract(FRAGCOORD.y * 0.5));
    col *= 1.0 - vignette * smoothstep(0.12, 0.62, d);
    COLOR = vec4(col, 1.0);
}
