package com.superscale

/**
 * All GLSL ES 3.00 shader sources.
 *
 * Algorithm summary (this is the "DLSS-like" pipeline, implemented with the
 * techniques that are actually viable on mobile GPUs):
 *
 *   Pass 1  RENDER   - the 3D/game content is rendered at a *fraction* of the
 *                      display resolution. This is where the performance comes
 *                      from. Cheap, and identical in spirit to what DLSS/FSR
 *                      consume as input.
 *   Pass 2  UPSCALE  - edge-directed, range-aware 16-tap Lanczos-2 resampling
 *                      (EASU-class spatial upscaling). Reconstructs edges
 *                      instead of smearing them like plain bilinear does.
 *   Pass 3  RCAS     - Contrast Adaptive Sharpening (the same algorithm family
 *                      AMD uses as the RCAS stage of FSR 1). Recovers the
 *                      high-frequency detail lost by rendering at low res,
 *                      without haloing flat areas.
 *
 * Passes 2 and 3 are the mobile equivalents of what DLSS does with a neural
 * network. DLSS itself cannot run on a phone: it requires NVIDIA Tensor Cores
 * and engine-level motion-vector data.
 */
object Shaders {

    /** Full-screen triangle. No vertex buffer needed beyond a 4-vertex quad. */
    const val VERT_QUAD = """#version 300 es
precision highp float;
layout(location = 0) in vec2 aPos;
out vec2 vUv;
void main() {
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    /**
     * Procedural demo scene. Deliberately full of thin lines, hard edges and
     * fine high-frequency detail, because that is exactly what exposes the
     * difference between bilinear and edge-directed upscaling.
     * Rendered directly at the (low) internal resolution.
     */
    const val FRAG_SCENE = """#version 300 es
precision highp float;
in vec2 vUv;
uniform vec2  uRes;
uniform float uTime;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    vec2 p = (vUv - 0.5) * vec2(uRes.x / uRes.y, 1.0);

    // ---- sky gradient -------------------------------------------------
    vec3 col = mix(vec3(0.02, 0.03, 0.07), vec3(0.06, 0.10, 0.20), vUv.y);

    // ---- star field (single-pixel high frequency detail) --------------
    vec2 cell = floor(vUv * uRes / 6.0);
    float star = step(0.94, hash(cell));
    col += vec3(0.35, 0.45, 0.65) * star;

    // ---- perspective ground grid (thin aliased lines) -----------------
    float horizon = 0.40;
    if (vUv.y < horizon) {
        float d = horizon - vUv.y;
        float z = 0.22 / max(d, 1e-4);
        float x = (vUv.x - 0.5) * z * 2.2;
        vec2 gp = vec2(x * 4.0, z * 4.0 + uTime * 1.4);
        vec2 fw = fwidth(gp) * 1.4 + 1e-5;
        vec2 gd = abs(fract(gp) - 0.5) / fw;
        float line = 1.0 - clamp(min(gd.x, gd.y), 0.0, 1.0);
        float fade = clamp(d * 3.2, 0.0, 1.0);
        col = mix(vec3(0.015, 0.022, 0.045), vec3(0.10, 0.62, 1.0),
                  line * fade);
    } else {
        // distant ridge line
        float ridge = 0.40 + 0.045 * sin(p.x * 6.0 + 1.3)
                            + 0.022 * sin(p.x * 15.0 + 0.4);
        if (vUv.y < ridge && vUv.y > 0.33) {
            col = mix(col, vec3(0.05, 0.09, 0.16), 0.85);
        }
    }

    // ---- three shaded spheres (smooth gradients + hard rims) ----------
    for (int i = 0; i < 3; ++i) {
        float fi = float(i);
        vec2  c  = vec2(-0.46 + fi * 0.46, 0.04 + 0.10 * sin(uTime * 0.7 + fi * 2.1));
        float r  = 0.15 + 0.012 * fi;
        vec2  q  = p - c;
        float dd = length(q);
        if (dd < r) {
            float z  = sqrt(max(r * r - dd * dd, 0.0));
            vec3  n  = normalize(vec3(q, z));
            vec3  L  = normalize(vec3(0.45, 0.65, 0.62));
            float dif = max(dot(n, L), 0.0);
            float spe = pow(max(dot(reflect(-n, L), vec3(0.0, 0.0, 1.0)), 0.0), 42.0);
            vec3  base = 0.5 + 0.5 * cos(vec3(0.0, 2.1, 4.2) + fi * 1.9);
            col = base * (0.18 + 0.82 * dif) + vec3(spe) + base * pow(1.0 - n.z, 2.5) * 0.55;
            // crisp rim highlight -> strong edge for the upscaler to chew on
            float rim = smoothstep(0.94, 1.0, dd / r);
            col += vec3(0.45) * rim;
        }
    }

    // ---- HUD-like panel with thin borders -----------------------------
    vec2 hudMin = vec2(0.035, 0.72);
    vec2 hudMax = vec2(0.40, 0.95);
    if (vUv.x > hudMin.x && vUv.x < hudMax.x &&
        vUv.y > hudMin.y && vUv.y < hudMax.y) {
        col = mix(col, vec3(0.02, 0.05, 0.07), 0.82);
        vec2 e = min(vUv - hudMin, hudMax - vUv);
        float border = 1.0 - smoothstep(0.0012, 0.0035, min(e.x, e.y));
        // horizontal "bars", 1px tall at internal res -> upscaler test
        float bar = 0.0;
        for (int b = 0; b < 4; ++b) {
            float by = hudMin.y + 0.032 + float(b) * 0.040;
            bar += 1.0 - smoothstep(0.0015, 0.0035, abs(vUv.y - by));
            float w = 0.10 + 0.09 * hash(vec2(float(b), 3.0));
            bar *= step(vUv.x, hudMin.x + 0.02 + w);
        }
        col = mix(col, vec3(0.17, 0.92, 0.76), clamp(border + bar, 0.0, 1.0));
    }

    fragColor = vec4(col, 1.0);
}
"""

    /** Plain bilinear stretch - the "before" baseline. */
    const val FRAG_BILINEAR = """#version 300 es
precision highp float;
in vec2 vUv;
uniform sampler2D uTex;
out vec4 fragColor;
void main() {
    fragColor = vec4(texture(uTex, vUv).rgb, 1.0);
}
"""

    /**
     * Edge-directed upscale: 16-tap Lanczos-2 window with range-aware
     * (bilateral) weighting. Each tap's contribution is attenuated when its
     * luminance differs from the centre pixel, so edges stay sharp instead of
     * being smeared across - this is the core of EASU-class upscaling.
     *
     * uniform uEdge: 0 = pure Lanczos (softer), 1..3 = progressively stricter
     * edge preservation.
     */
    const val FRAG_UPSCALE = """#version 300 es
precision highp float;
in vec2 vUv;
uniform sampler2D uTex;
uniform vec2  uSrcTexel;   // 1.0 / sourceSize
uniform vec2  uSrcSize;    // source dimensions in pixels
uniform float uEdge;
out vec4 fragColor;

const float PI = 3.141592653589793;

float lanczos2(float x) {
    x = abs(x);
    if (x < 1e-4) return 1.0;
    if (x >= 2.0) return 0.0;
    float px = PI * x;
    return (sin(px) * sin(px * 0.5)) / (0.5 * px * px);
}

void main() {
    // Position in source-pixel space (texel centres land on integers).
    vec2 pos  = vUv * uSrcSize - 0.5;
    vec2 base = floor(pos);
    vec2 fr   = pos - base;

    vec3  cols[16];
    float lums[16];

    int k = 0;
    float sumL = 0.0;
    for (int j = -1; j <= 2; ++j) {
        for (int i = -1; i <= 2; ++i) {
            vec2 sp = (base + vec2(float(i), float(j)) + 0.5) * uSrcTexel;
            // clamp to edge so borders do not darken
            sp = clamp(sp, vec2(0.0), vec2(1.0));
            vec3 c = texture(uTex, sp).rgb;
            cols[k] = c;
            lums[k] = dot(c, vec3(0.2126, 0.7152, 0.0722));
            sumL += lums[k];
            k++;
        }
    }

    float meanL = sumL * (1.0 / 16.0);

    // local luminance spread -> how aggressively to preserve edges
    float varL = 0.0;
    for (int m = 0; m < 16; ++m) {
        float d = lums[m] - meanL;
        varL += d * d;
    }
    varL *= (1.0 / 16.0);
    float spread = sqrt(varL);

    // reference luminance = centre (bilinear) sample
    float refL = dot(texture(uTex, vUv).rgb, vec3(0.2126, 0.7152, 0.0722));

    vec3  acc  = vec3(0.0);
    float wsum = 0.0;

    k = 0;
    for (int j = -1; j <= 2; ++j) {
        for (int i = -1; i <= 2; ++i) {
            float wx = lanczos2(float(i) - fr.x);
            float wy = lanczos2(float(j) - fr.y);
            float w  = wx * wy;

            float dl = (lums[k] - refL) / (spread + 0.015);
            w *= exp(-dl * dl * uEdge);

            acc  += cols[k] * w;
            wsum += w;
            k++;
        }
    }

    vec3 outc = (wsum > 1e-5) ? (acc / wsum) : texture(uTex, vUv).rgb;
    fragColor = vec4(outc, 1.0);
}
"""

    /**
     * RCAS - Contrast Adaptive Sharpening (AMD FidelityFX family, the same
     * stage FSR 1 uses). Adaptive amount per pixel: flat areas get almost no
     * sharpening (so noise is not amplified), high-contrast areas get more.
     * uniform uSharp: 0.0 = off, 1.0 = maximum.
     */
    const val FRAG_RCAS = """#version 300 es
precision highp float;
in vec2 vUv;
uniform sampler2D uTex;
uniform vec2  uTexel;
uniform float uSharp;
out vec4 fragColor;

void main() {
    vec3 c = texture(uTex, vUv).rgb;
    vec3 n = texture(uTex, vUv + vec2(0.0, -uTexel.y)).rgb;
    vec3 s = texture(uTex, vUv + vec2(0.0,  uTexel.y)).rgb;
    vec3 e = texture(uTex, vUv + vec2(uTexel.x,  0.0)).rgb;
    vec3 w = texture(uTex, vUv + vec2(-uTexel.x, 0.0)).rgb;

    vec3 mn = min(min(n, s), min(e, w));
    vec3 mx = max(max(n, s), max(e, w));

    // amplitude: 1 when the local ring is low-contrast, -> 0 near clipping
    vec3 amp = clamp(min(mn, 2.0 - mx) / max(mx, vec3(1e-5)), 0.0, 1.0);
    amp = sqrt(amp);

    float peak = -1.0 / (8.0 - 3.0 * clamp(uSharp, 0.0, 1.0));
    vec3  wgt  = amp * peak;

    vec3 outc = (n + s + e + w) * wgt + c;
    outc /= (1.0 + 4.0 * wgt);

    // anti-ringing: keep the result inside the local neighbourhood
    vec3 lo = min(mn, c);
    vec3 hi = max(mx, c);
    outc = clamp(outc, lo, hi);

    fragColor = vec4(outc, 1.0);
}
"""
}
