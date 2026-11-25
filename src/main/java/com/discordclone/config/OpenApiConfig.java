package com.discordclone.config;

import com.discordclone.websocket.event.WsEventType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
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
            // Not used by any REST operation, but the client needs it for WebSocket frames.
            openApi.getComponents().addSchemas("WsEventType", new StringSchema()
                    ._enum(Arrays.stream(WsEventType.values()).map(Enum::name).toList()));
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
