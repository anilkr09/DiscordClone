package com.discordclone.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The web client's types are generated from {@code frontend/openapi.json}. This fails when the API
 * no longer matches that file; run {@code ./gradlew test -PupdateOpenApi} and commit the result.
 */
@AutoConfigureMockMvc
class OpenApiContractTest extends IntegrationTest {

    private static final Path CONTRACT = Path.of("frontend/openapi.json");

    @Autowired
    MockMvc mvc;

    @Test
    void committedContractMatchesTheApi() throws Exception {
        String served = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        // Sorted keys keep the file stable from run to run.
        ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        String current = mapper.writeValueAsString(mapper.treeToValue(mapper.readTree(served), TreeMap.class)) + "\n";

        if (Boolean.getBoolean("updateOpenApi")) {
            Files.writeString(CONTRACT, current);
        }

        assertTrue(Files.exists(CONTRACT), "run ./gradlew test -PupdateOpenApi");
        JsonNode expected = mapper.readTree(Files.readString(CONTRACT));
        assertEquals(expected, mapper.readTree(current),
                "The API changed. Run ./gradlew test -PupdateOpenApi and commit frontend/openapi.json");
    }
}
