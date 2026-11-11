#version 330

// DEADLANDS (Hard Rules 3 and 4): light added on top of the lightmap.
// Import <minecraft:globals.glsl> first: it declares DeadlandsFlashlight.
//
// viewPos is the fragment's position in view space (the camera looks down -Z), so the cone is
// centred on the view and follows where the player looks. Light falls off with distance and is
// cut at the beam's range. A faint "near sight" term lets the eye make out what is within about
// two blocks in total darkness.

vec3 deadlands_extra_light(vec3 viewPos) {
    float dist = length(viewPos);
    vec3 light = vec3(0.045) * (1.0 - smoothstep(0.6, 2.4, dist));
    float strength = DeadlandsFlashlight.x;
    if (strength > 0.0 && dist > 0.001) {
        vec3 dir = viewPos / dist;
        float cone = smoothstep(DeadlandsFlashlight.z, DeadlandsFlashlight.y, -dir.z);
        float range = DeadlandsFlashlight.w;
        float falloff = (1.0 - smoothstep(range * 0.35, range, dist)) / (1.0 + 0.006 * dist * dist);
        light += vec3(1.0, 0.95, 0.84) * (strength * cone * falloff * 0.95);
    }
    return light;
}
