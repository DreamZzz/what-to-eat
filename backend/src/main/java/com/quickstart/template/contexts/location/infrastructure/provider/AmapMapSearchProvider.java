package com.quickstart.template.contexts.location.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickstart.template.contexts.location.api.dto.LocationSuggestionDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.map.provider", havingValue = "amap")
public class AmapMapSearchProvider implements MapSearchProvider {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String amapApiKey;

    public AmapMapSearchProvider(
            ObjectMapper objectMapper,
            @Value("${app.map.amap.base-url:https://restapi.amap.com/v3}") String amapBaseUrl,
            @Value("${app.map.amap.api-key:}") String amapApiKey) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(amapBaseUrl).build();
        this.amapApiKey = amapApiKey;
    }

    @Override
    public boolean isConfigured() {
        return !amapApiKey.isBlank();
    }

    @Override
    public List<LocationSuggestionDTO> search(String keyword) {
        if (keyword == null || keyword.isBlank() || !isConfigured()) {
            return Collections.emptyList();
        }

        String body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/assistant/inputtips")
                        .queryParam("key", amapApiKey)
                        .queryParam("keywords", keyword.trim())
                        .queryParam("datatype", "all")
                        .queryParam("citylimit", "false")
                        .build())
                .retrieve()
                .body(String.class);

        return parseSuggestions(body);
    }

    private List<LocationSuggestionDTO> parseSuggestions(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode tips = root.path("tips");
            if (!tips.isArray()) {
                return Collections.emptyList();
            }

            List<LocationSuggestionDTO> results = new ArrayList<>();
            for (JsonNode tip : tips) {
                String name = tip.path("name").asText("");
                String location = tip.path("location").asText("");
                if (name.isBlank() || location.isBlank() || !location.contains(",")) {
                    continue;
                }

                String[] parts = location.split(",");
                if (parts.length < 2) {
                    continue;
                }

                Double longitude = parseCoordinate(parts[0]);
                Double latitude = parseCoordinate(parts[1]);
                if (longitude == null || latitude == null) {
                    continue;
                }

                LocationSuggestionDTO item = new LocationSuggestionDTO();
                item.setName(name);
                item.setAddress(tip.path("address").asText(""));
                item.setDistrict(tip.path("district").asText(""));
                item.setCity(tip.path("cityname").asText(""));
                item.setLongitude(longitude);
                item.setLatitude(latitude);
                results.add(item);
            }
            return results;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse location suggestions", exception);
        }
    }

    private Double parseCoordinate(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
