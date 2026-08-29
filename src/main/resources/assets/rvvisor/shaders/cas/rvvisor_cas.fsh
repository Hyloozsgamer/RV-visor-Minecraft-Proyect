#version 150
uniform sampler2D uTexture;
uniform vec2 uInvResolution;
uniform float uSharpness;
in vec2 vUV;
out vec4 fragColor;

// AMD FidelityFX Contrast Adaptive Sharpening (CAS) v1.0
// Optimal for VR Displays (OLED / LCD Fast-Switch)
void main() {
    vec2 p = vUV;
    vec2 dx = vec2(uInvResolution.x, 0.0);
    vec2 dy = vec2(0.0, uInvResolution.y);

    // Fetch 3x3 Cross neighborhood
    vec3 a = texture(uTexture, p - dy).rgb; // Top
    vec3 b = texture(uTexture, p - dx).rgb; // Left
    vec3 c = texture(uTexture, p).rgb;      // Center
    vec3 d = texture(uTexture, p + dx).rgb; // Right
    vec3 e = texture(uTexture, p + dy).rgb; // Bottom

    // Diagonal samples for edge context
    vec3 tl = texture(uTexture, p - dx - dy).rgb;
    vec3 tr = texture(uTexture, p + dx - dy).rgb;
    vec3 bl = texture(uTexture, p - dx + dy).rgb;
    vec3 br = texture(uTexture, p + dx + dy).rgb;

    // Rec. 709 accurate perceived luminance weights
    vec3 lumaWeight = vec3(0.2126, 0.7152, 0.0722);
    float la = dot(a, lumaWeight);
    float lb = dot(b, lumaWeight);
    float lc = dot(c, lumaWeight);
    float ld = dot(d, lumaWeight);
    float le = dot(e, lumaWeight);

    float ltl = dot(tl, lumaWeight);
    float ltr = dot(tr, lumaWeight);
    float lbl = dot(bl, lumaWeight);
    float lbr = dot(br, lumaWeight);

    // Min and max local neighborhood bounds for anti-ringing
    float minLuma = min(min(min(la, lb), min(lc, ld)), min(le, min(min(ltl, ltr), min(lbl, lbr))));
    float maxLuma = max(max(max(la, lb), max(lc, ld)), max(le, max(max(ltl, ltr), max(lbl, lbr))));

    // Smooth contrast adaptation
    float amp = clamp(min(minLuma, 1.0 - maxLuma) / max(maxLuma - minLuma, 1e-5), 0.0, 1.0);
    amp = sqrt(amp);

    // Filter peak response
    float peak = -3.0 * uSharpness + 8.0;
    float w = -1.0 / (amp * peak + 1e-4);

    // Weighted kernel blend
    vec3 sharpened = (a + b + d + e) * (-w) + c * (1.0 + 4.0 * w);

    // Anti-ringing clamp to prevent overshoot halos in VR
    vec3 minColor = min(min(min(a, b), min(c, d)), e);
    vec3 maxColor = max(max(max(a, b), max(c, d)), e);
    sharpened = clamp(sharpened, minColor, maxColor);

    fragColor = vec4(sharpened, 1.0);
}
