package com.quickstart.template.contexts.location.infrastructure.provider;

import com.quickstart.template.contexts.location.api.dto.LocationSuggestionDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.map.provider", havingValue = "disabled", matchIfMissing = true)
public class DisabledMapSearchProvider implements MapSearchProvider {
    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public List<LocationSuggestionDTO> search(String keyword) {
        return Collections.emptyList();
    }
}
