#version 330

// Undertale-style scene: the world collapses into flat silhouettes by distance.
// Near things are black cut-outs, the middle distance a deep blue, the far fog and sky pale.
// Anything bright (lamps, the beacon crystal) punches through as a highlight.

uniform sampler2D InSampler;
uniform sampler2D InDepthSampler;

in vec2 texCoord;

layout(std140) uniform SilhouetteConfig {
    vec4 NearColor;
    vec4 MidColor;
    vec4 FarColor;
    float NearDistance;
    float FarDistance;
    float HighlightLuminance;
    // Width, in blocks, of the blend between neighbouring layers.
    float Softness;
    // How much of the real scene shows through the flat colours.
    float Detail;
    // Brightness range over which a lit pixel fades into a highlight; wider gives soft pools of light.
    float HighlightSoftness;
};

out vec4 fragColor;

// The world is rendered with reverse-Z (depth 1 at the camera, 0 at infinity) and a near plane of 0.05,
// so the view distance is simply near / depth.
const float NEAR_PLANE = 0.05;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float depth = texture(InDepthSampler, texCoord).r;
    float distance = NEAR_PLANE / max(depth, 1.0e-7);
    float luminance = dot(color, vec3(0.299, 0.587, 0.114));

    float nearToMid = smoothstep(NearDistance - Softness, NearDistance + Softness, distance);
    float midToFar = smoothstep(FarDistance - Softness, FarDistance + Softness, distance);
    vec3 layer = mix(mix(NearColor.rgb, MidColor.rgb, nearToMid), FarColor.rgb, midToFar);
    // Keep a trace of the real image so shapes and textures still read inside the silhouettes.
    layer += color * Detail;

    float highlight = smoothstep(HighlightLuminance - HighlightSoftness, HighlightLuminance, luminance);
    vec3 outColor = mix(layer, FarColor.rgb, highlight);
    fragColor = vec4(outColor, 1.0);
}
