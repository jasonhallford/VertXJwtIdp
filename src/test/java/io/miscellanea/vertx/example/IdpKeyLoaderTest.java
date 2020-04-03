package io.miscellanea.vertx.example;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

public class IdpKeyLoaderTest {
    private static JsonObject defaultConfig;

    // Test initializers
    @BeforeAll
    public static void loadDefaultConfig() {
        List<String> config = FileUtils.readTextFileFromClasspath("conf/idp-jwt-config.json");
        assertThat(config.size()).isGreaterThan(0);

        defaultConfig = (JsonObject) Json.decodeValue(String.join("\n", config));
        assertThat(defaultConfig).isNotNull();
    }

    // Test methods
    @Test
    @DisplayName("The loader asserts with a null configuration")
    public void assertsOnNullConfiguration() {
        AssertionError assertionError = catchThrowableOfType(
                () -> new IdpKeyLoader(IdpKeyLoader.KeyType.IdpPrivate, null), AssertionError.class);

        assertThat(assertionError).hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("The loader accepts the default configuration")
    public void defaultConfigurationIsAccepted() {
        new IdpKeyLoader(IdpKeyLoader.KeyType.IdpPrivate, defaultConfig);
    }

    @Test
    @DisplayName("Can load a private key from the classpath")
    public void canLoadPrivateKeyFromClasspath() {
        // Prepare for the test by loading the private key directly from the
        // classpath. We'll need this for comparison.
        List<String> lines = FileUtils.readTextFileFromClasspath("keys/idp-private.pem");
        String expectedKey = FileUtils.formatPemFileForVertx(lines);

        String key = new IdpKeyLoader(IdpKeyLoader.KeyType.IdpPrivate, defaultConfig).loadKey();
        assertThat(key).isNotEmpty();
        assertThat(key).isEqualTo(expectedKey);
    }

    @Test
    @DisplayName("Throws an exception when key file doesn't exist")
    public void throwsIdpExceptionWhenKeyFileDoesntExist() {
        IdpException expected = catchThrowableOfType(() -> {
            JsonObject config = new JsonObject().mergeIn(defaultConfig, true);
            config.put("idp-config-file", "c:/" + UUID.randomUUID().toString() + "/junk.key");

            new IdpKeyLoader(IdpKeyLoader.KeyType.IdpPrivate, config).loadKey();
        }, IdpException.class);
    }

    @Test
    @DisplayName("Can load a public key from the classpath")
    public void canLoadPublicKeyFromClasspath() {
        // Prepare for the test by loading the private key directly from the
        // classpath. We'll need this for comparison.
        List<String> lines = FileUtils.readTextFileFromClasspath("keys/idp-public.pem");
        String expectedKey = FileUtils.formatPemFileForVertx(lines);

        String key = new IdpKeyLoader(IdpKeyLoader.KeyType.IdpPublic, defaultConfig).loadKey();
        assertThat(key).isNotEmpty();
        assertThat(key).isEqualTo(expectedKey);
    }

    @Test
    @DisplayName("Can load a public key from the file system")
    public void canLoadPublicKeyFromFileSystem() throws IOException {
        // Prepare for the test by loading the private key directly from the
        // classpath. We'll need this for comparison.
        List<String> lines = FileUtils.readTextFileFromClasspath("keys/idp-public.pem");
        String expectedKey = String.join("\n", lines);

        // Write the key to a temporary file
        File temp = File.createTempFile("junit-pubkey", ".pem");
        try {
            Files.writeString(temp.toPath(), expectedKey);

            // Add the file to the configuration
            JsonObject config = new JsonObject().mergeIn(defaultConfig, true);
            var keys = new JsonObject().put("public", temp.getAbsolutePath()).put("private", temp.getAbsolutePath());
            config.put("keys", keys);

            String key = new IdpKeyLoader(IdpKeyLoader.KeyType.IdpPublic, config).loadKey();
            assertThat(key).isNotEmpty();
            assertThat(key).isEqualTo(FileUtils.formatPemFileForVertx(lines));
        } finally {
            if (temp.exists()) {
                assertThat(temp.delete()).isTrue();
            }
        }
    }
}
