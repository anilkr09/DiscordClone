package com.discordclone.config;

import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

@Configuration
public class OpenApiConfig {

    /**
     * Responses always carry every field, so mark response properties required; otherwise the
     * generated client types would all be optional. Request schemas keep their validation rules.
     * The result is also made independent of the server's address, so it can be committed.
     */
    @Bean
    OpenApiCustomizer contractCustomizer() {
        return openApi -> {
            openApi.setServers(null);
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            schemas.forEach((name, schema) -> {
                Map<String, Schema> properties = schema.getProperties();
                if (properties != null && !name.endsWith("Request") && !name.endsWith("Payload")) {
                    schema.setRequired(new ArrayList<>(new TreeMap<>(properties).keySet()));
                }
            });
        };
    }
}
