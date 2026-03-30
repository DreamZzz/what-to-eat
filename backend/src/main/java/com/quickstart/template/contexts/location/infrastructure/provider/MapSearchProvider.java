package com.quickstart.template.contexts.location.infrastructure.provider;

import com.quickstart.template.contexts.location.api.dto.LocationSuggestionDTO;

import java.util.List;

public interface MapSearchProvider {
    boolean isConfigured();

    List<LocationSuggestionDTO> search(String keyword);
}
