package com.quickstart.template.contexts.location.api;

import com.quickstart.template.contexts.location.api.dto.LocationSuggestionDTO;
import com.quickstart.template.shared.dto.MessageResponse;
import com.quickstart.template.contexts.location.application.LocationSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class LocationController {
    private final LocationSearchService locationSearchService;

    public LocationController(LocationSearchService locationSearchService) {
        this.locationSearchService = locationSearchService;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String keyword) {
        if (!locationSearchService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new MessageResponse("地图服务未配置", "map", "disabled", true));
        }

        try {
            List<LocationSuggestionDTO> results = locationSearchService.search(keyword);
            return ResponseEntity.ok(results);
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new MessageResponse("地图搜索失败"));
        }
    }
}
