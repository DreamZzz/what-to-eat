package com.quickstart.template.contexts.meal.api;

import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.meal.api.dto.MealCatalogResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.MealIntentRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.MealIntentResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecipeCollectionResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeImageResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipePreferenceRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeStepTokenDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipePreferenceResponseDTO;
import com.quickstart.template.contexts.meal.application.MealGenerationException;
import com.quickstart.template.contexts.meal.application.MealService;
import com.quickstart.template.platform.security.CurrentUserService;
import com.quickstart.template.shared.dto.MessageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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

    @PostMapping("/intent")
    public ResponseEntity<?> analyzeIntent(@Valid @RequestBody MealIntentRequestDTO request) {
        Optional<Long> currentUserId = getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Authentication required"));
        }

        MealIntentResponseDTO response = mealService.analyzeIntent(request.getSourceText());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/recommendations/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRecommendations(@Valid @RequestBody MealRecommendationRequestDTO request) {
        Optional<Long> currentUserId = getCurrentUserId();
        SseEmitter emitter = new SseEmitter(120_000L);

        if (currentUserId.isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"message\":\"Authentication required\"}"));
            } catch (IOException ignored) {
            }
            emitter.complete();
            return emitter;
        }

        Long userId = currentUserId.get();

        emitter.onTimeout(() -> sendErrorAndComplete(emitter, "生成超时，请重试"));

        CompletableFuture.runAsync(() -> {
            try {
                mealService.streamRecommendations(request, userId, reasonSummary -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("summary")
                                .data(new MealStreamSummaryResponse(reasonSummary), MediaType.APPLICATION_JSON));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE write failed", e);
                    }
                }, recipe -> {
                    try {
                        emitter.send(SseEmitter.event().name("recipe").data(recipe, MediaType.APPLICATION_JSON));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE write failed", e);
                    }
                });
                emitter.send(SseEmitter.event().name("done").data("{\"complete\":true}", MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (MealGenerationException e) {
                sendErrorAndComplete(emitter, e.getMessage());
            } catch (Exception e) {
                sendErrorAndComplete(emitter, "生成失败，请重试");
            }
        });

        return emitter;
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

    @PostMapping(value = "/recipes/{recipeId}/steps/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRecipeSteps(
            @PathVariable Long recipeId,
            @RequestParam(defaultValue = "zh-CN") String locale) {
        Optional<Long> currentUserId = getCurrentUserId();
        SseEmitter emitter = new SseEmitter(90_000L);

        if (currentUserId.isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"message\":\"Authentication required\"}"));
            } catch (IOException ignored) {
            }
            emitter.complete();
            return emitter;
        }

        Long userId = currentUserId.get();
        emitter.onTimeout(() -> sendErrorAndComplete(emitter, "步骤生成超时，请重试"));

        CompletableFuture.runAsync(() -> {
            try {
                mealService.streamRecipeSteps(recipeId, userId, locale, token -> {
                    try {
                        emitter.send(SseEmitter.event().name("token").data(token, MediaType.APPLICATION_JSON));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE write failed", e);
                    }
                }, step -> {
                    try {
                        emitter.send(SseEmitter.event().name("step").data(step, MediaType.APPLICATION_JSON));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE write failed", e);
                    }
                }, () -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data("{\"complete\":true}", MediaType.APPLICATION_JSON));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE write failed", e);
                    }
                });
                emitter.complete();
            } catch (IllegalArgumentException e) {
                sendErrorAndComplete(emitter, "未找到该菜谱");
            } catch (MealGenerationException e) {
                sendErrorAndComplete(emitter, e.getMessage());
            } catch (Exception e) {
                sendErrorAndComplete(emitter, "步骤生成失败，请重试");
            }
        });

        return emitter;
    }

    @PostMapping("/recipes/{recipeId}/image")
    public ResponseEntity<?> fetchRecipeImage(@PathVariable Long recipeId) {
        Optional<Long> currentUserId = getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Authentication required"));
        }

        Optional<RecipeImageResponseDTO> result = mealService.fetchRecipeImage(recipeId, currentUserId.get());
        return result.<ResponseEntity<?>>map(ResponseEntity::ok)
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

        try {
            Optional<RecipePreferenceResponseDTO> response = mealService.updatePreference(
                    recipeId,
                    request.getPreference(),
                    currentUserId.get()
            );
            return response.<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponse("Recipe not found")));
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

    private void sendErrorAndComplete(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data(new MessageResponse(message), MediaType.APPLICATION_JSON));
        } catch (IOException ignored) {
        }
        emitter.complete();
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

    private record MealStreamSummaryResponse(String reasonSummary) {
    }
}
