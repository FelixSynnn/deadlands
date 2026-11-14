package com.huaxiaosheng.spapacks.rig;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class RigResources {
    private RigResources() {
    }

    static Reader open(String resource) {
        InputStream stream = Objects.requireNonNull(RigResources.class.getResourceAsStream("/assets/spa_packs/rig/" + resource), "Missing resource " + resource);
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
}
