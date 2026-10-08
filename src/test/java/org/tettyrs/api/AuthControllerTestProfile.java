package org.tettyrs.api;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class AuthControllerTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of();
    }
}
