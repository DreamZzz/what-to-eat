package com.quickstart.template.contexts.location.api;

import com.quickstart.template.contexts.location.api.dto.LocationSuggestionDTO;
import com.quickstart.template.platform.security.JwtUtils;
import com.quickstart.template.platform.security.SecurityConfig;
import com.quickstart.template.contexts.location.application.LocationSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocationController.class)
@Import(SecurityConfig.class)
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocationSearchService locationSearchService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    @DisplayName("GET /api/locations/search should be accessible anonymously and return 503 when provider is not configured")
    void search_ShouldReturnServiceUnavailable_WhenMapProviderNotConfigured() throws Exception {
        when(locationSearchService.isConfigured()).thenReturn(false);

        mockMvc.perform(get("/api/locations/search").param("keyword", "三里屯"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("地图服务未配置"));
    }

    @Test
    @DisplayName("GET /api/locations/search should be accessible anonymously and return search results")
    void search_ShouldReturnResults_WhenConfigured() throws Exception {
        LocationSuggestionDTO item = new LocationSuggestionDTO();
        item.setName("三里屯太古里");
        item.setAddress("三里屯路11号");
        item.setDistrict("北京市朝阳区");
        item.setCity("北京市");
        item.setLatitude(39.934871);
        item.setLongitude(116.45399);

        when(locationSearchService.isConfigured()).thenReturn(true);
        when(locationSearchService.search("三里屯")).thenReturn(List.of(item));

        mockMvc.perform(get("/api/locations/search").param("keyword", "三里屯"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("三里屯太古里"))
                .andExpect(jsonPath("$[0].address").value("三里屯路11号"))
                .andExpect(jsonPath("$[0].district").value("北京市朝阳区"))
                .andExpect(jsonPath("$[0].city").value("北京市"))
                .andExpect(jsonPath("$[0].latitude").value(39.934871))
                .andExpect(jsonPath("$[0].longitude").value(116.45399));

        verify(locationSearchService).search("三里屯");
    }

    @Test
    @DisplayName("GET /api/locations/search should return 502 when map search provider fails")
    void search_ShouldReturnBadGateway_WhenProviderFails() throws Exception {
        when(locationSearchService.isConfigured()).thenReturn(true);
        when(locationSearchService.search("三里屯")).thenThrow(new IllegalStateException("provider error"));

        mockMvc.perform(get("/api/locations/search").param("keyword", "三里屯"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("地图搜索失败"));
    }
}
