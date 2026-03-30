package com.quickstart.template.contexts.location.application;

import com.quickstart.template.contexts.location.infrastructure.provider.MapSearchProvider;
import com.quickstart.template.contexts.location.api.dto.LocationSuggestionDTO;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class LocationSearchService {
    private final MapSearchProvider mapSearchProvider;

    public LocationSearchService(MapSearchProvider mapSearchProvider) {
        this.mapSearchProvider = mapSearchProvider;
    }

    public boolean isConfigured() {
        return mapSearchProvider.isConfigured();
    }

    public List<LocationSuggestionDTO> search(String keyword) {
        return mapSearchProvider.search(keyword);
    }
}
