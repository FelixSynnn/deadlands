#version 330

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
    // DEADLANDS: flashlight strength, cos inner half-angle, cos outer half-angle, range (blocks).
    vec4 DeadlandsFlashlight;
};
