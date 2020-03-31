package io.miscellanea.vetx.example;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public interface Utils {
    Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    static List<String> readTextFileFromClasspath(String resourcePath) {
        List<String> contents = new ArrayList<>();

        try (var in = (BufferedInputStream) Utils.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IdpException("Unable to locate resource '" + resourcePath + "' on the classpath.");
            }
            var reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                contents.add(line);
            }
        } catch (IdpException i) {
            throw i;
        } catch (Exception e) {
            throw new IdpException("Unable to load resource '" + resourcePath + "' from the classpath.", e);
        }

        return contents;
    }

    static String formatPemFileForVertx(List<String> pemAsList) {
        assert pemAsList != null : "pemAsList must not be null.";

        return pemAsList.stream()
                .filter(line -> !line.startsWith("--") &&
                        !line.toLowerCase().startsWith("proc") &&
                        !line.toLowerCase().startsWith("dek") &&
                        !line.isEmpty())
                .collect(Collectors.joining("\n"));
    }
}
