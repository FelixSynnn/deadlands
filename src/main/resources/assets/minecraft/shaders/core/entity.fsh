#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:deadlands_light.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 dlViewPos;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
#ifdef PER_FACE_LIGHTING
    color *= (gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack) * ColorModulator;
#else
    color *= vertexColor * ColorModulator;
#endif
#ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#endif
#ifndef EMISSIVE
    color *= vec4(min(lightMapColor.rgb + deadlands_extra_light(dlViewPos), vec3(1.0)), lightMapColor.a);
#endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
