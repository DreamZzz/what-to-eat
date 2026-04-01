package com.quickstart.template.contexts.meal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.meal.api.dto.MealCatalogItemDTO;
import com.quickstart.template.contexts.meal.api.dto.MealCatalogResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationFormDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecipeCollectionResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeIngredientDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipePreferenceResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeStepDTO;
import com.quickstart.template.contexts.meal.application.MealService;
import com.quickstart.template.platform.security.CurrentUserService;
import com.quickstart.template.platform.security.JwtUtils;
import com.quickstart.template.platform.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MealController.class)
@Import(SecurityConfig.class)
class MealControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MealService mealService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    @DisplayName("POST /api/meals/recommendations should return generated recipes")
    void recommend_ShouldReturnGeneratedRecipes() throws Exception {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("demo_admin");

        RecipeDTO recipe = new RecipeDTO();
        recipe.setId(99L);
        recipe.setTitle("番茄鸡蛋面");
        recipe.setSummary("一碗温暖的家常面");
        recipe.setEstimatedCalories(520);

        RecipeIngredientDTO ingredient = new RecipeIngredientDTO();
        ingredient.setName("番茄");
        ingredient.setAmount("2个");
        recipe.setIngredients(List.of(ingredient));

        RecipeStepDTO step = new RecipeStepDTO();
        step.setIndex(1);
        step.setContent("先炒番茄。");
        recipe.setSteps(List.of(step));

        MealRecommendationFormDTO form = new MealRecommendationFormDTO();
        form.setSourceMode("TEXT");
        form.setCatalogItemId(51L);
        form.setDishCount(1);
        form.setTotalCalories(600);
        form.setStaple("NOODLES");
        form.setLocale("zh-CN");

        MealRecommendationResponseDTO response = new MealRecommendationResponseDTO();
        response.setRequestId("req-1");
        response.setSourceText("番茄鸡蛋面");
        response.setForm(form);
        response.setProvider("mock");
        response.setItems(List.of(recipe));
        response.setEmptyState(false);

        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(mealService.recommendRecipes(any(MealRecommendationRequestDTO.class), eq(1L))).thenReturn(response);

        mockMvc.perform(post("/api/meals/recommendations")
                        .with(user("demo_admin").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceText": "番茄鸡蛋面",
                                  "sourceMode": "TEXT",
                                  "catalogItemId": 51,
                                  "dishCount": 1,
                                  "totalCalories": 600,
                                  "staple": "NOODLES",
                                  "locale": "zh-CN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-1"))
                .andExpect(jsonPath("$.items[0].title").value("番茄鸡蛋面"))
                .andExpect(jsonPath("$.form.catalogItemId").value(51))
                .andExpect(jsonPath("$.form.dishCount").value(1))
                .andExpect(jsonPath("$.provider").value("mock"));
    }

    @Test
    @DisplayName("GET /api/meals/catalog should return catalog items")
    void getCatalog_ShouldReturnCatalogItems() throws Exception {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("demo_admin");

        MealCatalogItemDTO item = new MealCatalogItemDTO();
        item.setId(51L);
        item.setCode("cn-home-051");
        item.setName("番茄炒蛋");
        item.setCategory("蛋豆");
        item.setSubcategory("鸡蛋");
        item.setCookingMethod("炒");
        item.setFlavorTags(List.of("酸甜"));
        item.setFeatureTags(List.of("国民家常"));
        item.setIngredientTags(List.of("番茄", "鸡蛋"));
        item.setSourceIndex(51);

        MealCatalogResponseDTO response = new MealCatalogResponseDTO();
        response.setDatasetVersion("cn-home-menu-v1");
        response.setTotal(1);
        response.setItems(List.of(item));

        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(mealService.getCatalog()).thenReturn(response);

        mockMvc.perform(get("/api/meals/catalog")
                        .with(user("demo_admin").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetVersion").value("cn-home-menu-v1"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("番茄炒蛋"))
                .andExpect(jsonPath("$.items[0].ingredientTags[0]").value("番茄"));
    }

    @Test
    @DisplayName("PUT /api/meals/recipes/{recipeId}/preference should return updated preference")
    void updatePreference_ShouldReturnUpdatedPreference() throws Exception {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("demo_admin");

        RecipePreferenceResponseDTO response = new RecipePreferenceResponseDTO();
        response.setRecipeId(7L);
        response.setPreference("LIKE");

        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(mealService.updatePreference(eq(7L), eq("LIKE"), eq(1L))).thenReturn(Optional.of(response));

        mockMvc.perform(put("/api/meals/recipes/7/preference")
                        .with(user("demo_admin").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preference": "LIKE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipeId").value(7))
                .andExpect(jsonPath("$.preference").value("LIKE"));
    }

    @Test
    @DisplayName("GET /api/meals/favorites should return paged favorites")
    void getFavorites_ShouldReturnPagedFavorites() throws Exception {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("demo_admin");

        RecipeDTO recipe = new RecipeDTO();
        recipe.setId(7L);
        recipe.setTitle("清炒时蔬");
        recipe.setPreference("LIKE");

        MealRecipeCollectionResponseDTO response = MealRecipeCollectionResponseDTO.fromPage(
                List.of(recipe),
                new PageImpl<>(List.of(recipe), PageRequest.of(0, 10), 1),
                "favorites",
                null,
                "latest",
                "database"
        );

        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(mealService.getFavorites(eq(1L), any())).thenReturn(response);

        mockMvc.perform(get("/api/meals/favorites")
                        .with(user("demo_admin").roles("USER"))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("清炒时蔬"))
                .andExpect(jsonPath("$.retrieval.scene").value("favorites"))
                .andExpect(jsonPath("$.retrieval.provider").value("database"));
    }

    @Test
    @DisplayName("GET /api/meals/favorites should return 401 when unauthenticated")
    void getFavorites_ShouldReturnUnauthorizedWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/meals/favorites")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isUnauthorized());
    }
}
