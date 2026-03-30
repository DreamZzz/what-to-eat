package com.quickstart.template.contexts.meal.api;

import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.meal.api.dto.MealCatalogResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecipeCollectionResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipePreferenceRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipePreferenceResponseDTO;
import com.quickstart.template.contexts.meal.application.MealGenerationException;
import com.quickstart.template.contexts.meal.application.MealService;
import com.quickstart.template.platform.security.CurrentUserService;
import com.quickstart.template.shared.dto.MessageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/meals")
@PreAuthorize("isAuthenticated()")
public class MealController {
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final MealService mealService;
    private final CurrentUserService currentUserService;

    public MealController(MealService mealService, CurrentUserService currentUserService) {
        this.mealService = mealService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/recommendations")
    public ResponseEntity<?> recommend(@Valid @RequestBody MealRecommendationRequestDTO request) {
        Optional<Long> currentUserId = getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Authentication required"));
        }

        try {
            MealRecommendationResponseDTO response = mealService.recommendRecipes(request, currentUserId.get());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new MessageResponse(exception.getMessage()));
        } catch (MealGenerationException exception) {
            HttpStatus status = exception.isConfiguration() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            return ResponseEntity.status(status).body(new MessageResponse(
                    exception.getMessage(),
                    "recipe-ai",
                    exception.isConfiguration() ? "mock" : "openai-compatible",
                    exception.isConfiguration()
            ));
        }
    }

    @GetMapping("/catalog")
    public ResponseEntity<?> getCatalog() {
        Optional<Long> currentUserId = getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Authentication required"));
        }

        MealCatalogResponseDTO response = mealService.getCatalog();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/recipes/{recipeId}")
    public ResponseEntity<?> getRecipe(@PathVariable Long recipeId) {
        Optional<Long> currentUserId = getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Authentication required"));
        }

        Optional<RecipeDTO> recipe = mealService.getRecipe(recipeId, currentUserId.get());
        return recipe.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponse("Recipe not found")));
    }

    @PutMapping("/recipes/{recipeId}/preference")
    public ResponseEntity<?> updatePreference(
            @PathVariable Long recipeId,
            @Valid @RequestBody RecipePreferenceRequestDTO request) {
        Optional<Long> currentUserId = getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Authentication required"));
        }

        Optional<RecipePreferenceResponseDTO> response = mealService.updatePreference(recipeId, request.getPreference(), currentUserId.get());
        return response.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponse("Recipe not found")));
    }

    @GetMapping("/favorites")
    public ResponseEntity<?> getFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Optional<Long> currentUserId = getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Authentication required"));
        }

        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizePageSize(size));
        MealRecipeCollectionResponseDTO response = mealService.getFavorites(currentUserId.get(), pageable);
        return ResponseEntity.ok(response);
    }

    private Optional<Long> getCurrentUserId() {
        return currentUserService.getCurrentUser().map(User::getId);
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, DEFAULT_PAGE_SIZE);
    }
}
