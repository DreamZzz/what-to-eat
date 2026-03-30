package com.quickstart.template.contexts.meal.application;

import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.account.infrastructure.persistence.UserRepository;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecommendationResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.MealRecipeCollectionResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeIngredientDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipePreferenceResponseDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeStepDTO;
import com.quickstart.template.contexts.meal.domain.MealCatalogItem;
import com.quickstart.template.contexts.meal.domain.MealRecipe;
import com.quickstart.template.contexts.meal.infrastructure.persistence.MealRecipeRepository;
import com.quickstart.template.platform.provider.recipeai.MealGenerationProvider;
import com.quickstart.template.platform.provider.recipeai.MealImageProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealServiceTest {
    @Mock
    private MealRecipeRepository mealRecipeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MealGenerationProvider mealGenerationProvider;

    @Mock
    private MealImageProvider mealImageProvider;

    @Mock
    private MealCatalogService mealCatalogService;

    private final MealRecipeMapper mealRecipeMapper = new MealRecipeMapper(new ObjectMapper());
    private MealService mealService;

    private User user;

    @BeforeEach
    void setUp() {
        mealService = new MealService(
                mealRecipeRepository,
                userRepository,
                mealGenerationProvider,
                mealImageProvider,
                mealRecipeMapper,
                mealCatalogService
        );
        user = new User();
        user.setId(1L);
        user.setUsername("demo_admin");
    }

    @Test
    @DisplayName("recommendRecipes should persist generated recipes and attach ids")
    void recommendRecipes_ShouldPersistGeneratedRecipesAndAttachIds() {
        MealRecommendationRequestDTO request = new MealRecommendationRequestDTO();
        request.setSourceText("番茄鸡蛋面");
        request.setSourceMode("TEXT");
        request.setCatalogItemId(11L);
        request.setDishCount(1);
        request.setTotalCalories(600);
        request.setStaple("NOODLES");
        request.setFlavor("LIGHT");
        request.setLocale("zh-CN");

        RecipeDTO recipe = new RecipeDTO();
        recipe.setTitle("番茄鸡蛋面");
        recipe.setSummary("温暖的一碗面");
        recipe.setEstimatedCalories(520);
        RecipeIngredientDTO ingredient = new RecipeIngredientDTO();
        ingredient.setName("番茄");
        ingredient.setAmount("2个");
        recipe.setIngredients(List.of(ingredient));
        RecipeStepDTO step = new RecipeStepDTO();
        step.setIndex(1);
        step.setContent("煮面。");
        recipe.setSteps(List.of(step));

        MealCatalogItem catalogItem = new MealCatalogItem();
        catalogItem.setId(11L);
        catalogItem.setCode("cn-home-011");
        catalogItem.setName("番茄炒蛋");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(mealCatalogService.findItemById(11L)).thenReturn(Optional.of(catalogItem));
        when(mealRecipeRepository.findLatestReusableRequestId(
                eq("番茄鸡蛋面"),
                eq(1),
                eq(600),
                eq("NOODLES"),
                eq("LIGHT"),
                eq("zh-CN"),
                eq(11L)
        )).thenReturn(Optional.empty());
        when(mealGenerationProvider.generate(request)).thenReturn(new MealGenerationResult("mock", List.of(recipe), false));
        when(mealImageProvider.generate(eq(request), any(RecipeDTO.class)))
                .thenReturn(new MealImageResult("disabled", null, "OMITTED"));
        when(mealRecipeRepository.save(any(MealRecipe.class))).thenAnswer(invocation -> {
            MealRecipe saved = invocation.getArgument(0);
            saved.setId(77L);
            saved.setUpdatedAt(LocalDateTime.of(2026, 3, 28, 12, 0));
            return saved;
        });

        MealRecommendationResponseDTO response = mealService.recommendRecipes(request, 1L);

        assertEquals("mock", response.getProvider());
        assertEquals(1, response.getItems().size());
        assertEquals(77L, response.getItems().get(0).getId());
        assertEquals(11L, response.getItems().get(0).getCatalogItemId());
        assertEquals("番茄鸡蛋面", response.getItems().get(0).getTitle());
        assertFalse(response.getEmptyState());

        ArgumentCaptor<MealRecipe> captor = ArgumentCaptor.forClass(MealRecipe.class);
        verify(mealRecipeRepository).save(captor.capture());
        assertEquals("mock", captor.getValue().getProvider());
        assertEquals(11L, captor.getValue().getCatalogItem().getId());
        assertEquals("cn-home-011", captor.getValue().getCatalogItemCode());
        assertEquals("TEXT", captor.getValue().getSourceMode());
        assertNotNull(captor.getValue().getRequestId());
    }

    @Test
    @DisplayName("recommendRecipes should reuse the current user's previously generated recipes from database")
    void recommendRecipes_ShouldReuseCurrentUsersRecipesFromDatabase() {
        MealRecommendationRequestDTO request = new MealRecommendationRequestDTO();
        request.setSourceText("番茄鸡蛋面");
        request.setSourceMode("VOICE");
        request.setCatalogItemId(11L);
        request.setDishCount(1);
        request.setTotalCalories(600);
        request.setStaple("NOODLES");
        request.setFlavor("LIGHT");
        request.setLocale("zh-CN");

        MealCatalogItem catalogItem = new MealCatalogItem();
        catalogItem.setId(11L);
        catalogItem.setCode("cn-home-011");
        catalogItem.setName("番茄炒蛋");

        MealRecipe cachedRecipe = new MealRecipe();
        cachedRecipe.setId(201L);
        cachedRecipe.setRequestId("cached-request-1");
        cachedRecipe.setUser(user);
        cachedRecipe.setCatalogItem(catalogItem);
        cachedRecipe.setCatalogItemCode("cn-home-011");
        cachedRecipe.setTitle("番茄鸡蛋面");
        cachedRecipe.setSummary("上次已经生成过");
        cachedRecipe.setImageStatus("GENERATED");
        cachedRecipe.setImageUrl("https://example.com/tomato-egg-noodle.jpg");
        cachedRecipe.setPreference("LIKE");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(mealCatalogService.findItemById(11L)).thenReturn(Optional.of(catalogItem));
        when(mealRecipeRepository.findLatestReusableRequestId(
                eq("番茄鸡蛋面"),
                eq(1),
                eq(600),
                eq("NOODLES"),
                eq("LIGHT"),
                eq("zh-CN"),
                eq(11L)
        )).thenReturn(Optional.of("cached-request-1"));
        when(mealRecipeRepository.findAllByRequestIdOrderByIdAsc("cached-request-1"))
                .thenReturn(List.of(cachedRecipe));

        MealRecommendationResponseDTO response = mealService.recommendRecipes(request, 1L);

        assertEquals("database", response.getProvider());
        assertEquals("cached-request-1", response.getRequestId());
        assertEquals(1, response.getItems().size());
        assertEquals(201L, response.getItems().get(0).getId());
        assertEquals("番茄鸡蛋面", response.getItems().get(0).getTitle());
        assertEquals("LIKE", response.getItems().get(0).getPreference());

        verify(mealGenerationProvider, never()).generate(any());
        verify(mealImageProvider, never()).generate(any(), any());
        verify(mealRecipeRepository, never()).save(any());
    }

    @Test
    @DisplayName("recommendRecipes should clone cached recipes for another user without regenerating content")
    void recommendRecipes_ShouldCloneCachedRecipesForAnotherUser() {
        MealRecommendationRequestDTO request = new MealRecommendationRequestDTO();
        request.setSourceText("番茄鸡蛋面");
        request.setSourceMode("TEXT");
        request.setCatalogItemId(11L);
        request.setDishCount(1);
        request.setTotalCalories(600);
        request.setStaple("NOODLES");
        request.setFlavor("LIGHT");
        request.setLocale("zh-CN");

        MealCatalogItem catalogItem = new MealCatalogItem();
        catalogItem.setId(11L);
        catalogItem.setCode("cn-home-011");
        catalogItem.setName("番茄炒蛋");

        User anotherUser = new User();
        anotherUser.setId(2L);
        anotherUser.setUsername("other_user");

        MealRecipe cachedRecipe = new MealRecipe();
        cachedRecipe.setId(202L);
        cachedRecipe.setRequestId("cached-request-2");
        cachedRecipe.setUser(anotherUser);
        cachedRecipe.setCatalogItem(catalogItem);
        cachedRecipe.setCatalogItemCode("cn-home-011");
        cachedRecipe.setProvider("openai-compatible");
        cachedRecipe.setTitle("番茄鸡蛋面");
        cachedRecipe.setSummary("来自别人的缓存");
        cachedRecipe.setImageStatus("GENERATED");
        cachedRecipe.setImageUrl("https://example.com/tomato-egg-noodle.jpg");
        cachedRecipe.setPreference("DISLIKE");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(mealCatalogService.findItemById(11L)).thenReturn(Optional.of(catalogItem));
        when(mealRecipeRepository.findLatestReusableRequestId(
                eq("番茄鸡蛋面"),
                eq(1),
                eq(600),
                eq("NOODLES"),
                eq("LIGHT"),
                eq("zh-CN"),
                eq(11L)
        )).thenReturn(Optional.of("cached-request-2"));
        when(mealRecipeRepository.findAllByRequestIdOrderByIdAsc("cached-request-2"))
                .thenReturn(List.of(cachedRecipe));
        when(mealRecipeRepository.save(any(MealRecipe.class))).thenAnswer(invocation -> {
            MealRecipe saved = invocation.getArgument(0);
            saved.setId(303L);
            saved.setUpdatedAt(LocalDateTime.of(2026, 3, 29, 21, 0));
            return saved;
        });

        MealRecommendationResponseDTO response = mealService.recommendRecipes(request, 1L);

        assertEquals("database", response.getProvider());
        assertEquals(1, response.getItems().size());
        assertEquals(303L, response.getItems().get(0).getId());
        assertEquals("番茄鸡蛋面", response.getItems().get(0).getTitle());
        assertNull(response.getItems().get(0).getPreference());

        ArgumentCaptor<MealRecipe> captor = ArgumentCaptor.forClass(MealRecipe.class);
        verify(mealRecipeRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getUser().getId());
        assertEquals("openai-compatible", captor.getValue().getProvider());
        assertNull(captor.getValue().getPreference());
        assertNotNull(captor.getValue().getRequestId());

        verify(mealGenerationProvider, never()).generate(any());
        verify(mealImageProvider, never()).generate(any(), any());
    }

    @Test
    @DisplayName("updatePreference should upsert preference for the current user")
    void updatePreference_ShouldUpsertPreference() {
        MealRecipe recipe = new MealRecipe();
        recipe.setId(9L);
        recipe.setPreference(null);

        when(mealRecipeRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(recipe));
        when(mealRecipeRepository.save(any(MealRecipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<RecipePreferenceResponseDTO> response = mealService.updatePreference(9L, "LIKE", 1L);

        assertTrue(response.isPresent());
        assertEquals(9L, response.get().getRecipeId());
        assertEquals("LIKE", response.get().getPreference());
        assertNotNull(response.get().getUpdatedAt());
        assertEquals("LIKE", recipe.getPreference());
    }

    @Test
    @DisplayName("getFavorites should return a paged recipe collection")
    void getFavorites_ShouldReturnPagedRecipeCollection() {
        MealRecipe favorite = new MealRecipe();
        favorite.setId(5L);
        favorite.setTitle("清炒时蔬");
        favorite.setSummary("清爽下饭");
        favorite.setPreference("LIKE");

        when(mealRecipeRepository.findAllByUserIdAndPreferenceOrderByUpdatedAtDesc(eq(1L), eq("LIKE"), any()))
                .thenReturn(new PageImpl<>(List.of(favorite), PageRequest.of(0, 10), 1));

        MealRecipeCollectionResponseDTO response = mealService.getFavorites(1L, PageRequest.of(0, 10));

        assertEquals("favorites", response.getRetrieval().getScene());
        assertEquals("database", response.getRetrieval().getProvider());
        assertEquals(1, response.getItems().size());
        assertEquals("清炒时蔬", response.getItems().get(0).getTitle());
        assertEquals(1L, response.getPagination().getTotalItems());
    }
}
