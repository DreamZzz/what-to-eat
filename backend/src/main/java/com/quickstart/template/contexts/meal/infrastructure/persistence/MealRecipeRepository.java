package com.quickstart.template.contexts.meal.infrastructure.persistence;

import com.quickstart.template.contexts.meal.domain.MealRecipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

public interface MealRecipeRepository extends JpaRepository<MealRecipe, Long> {
    @EntityGraph(attributePaths = {"catalogItem"})
    Optional<MealRecipe> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"catalogItem"})
    Page<MealRecipe> findAllByUserIdAndPreferenceOrderByUpdatedAtDesc(Long userId, String preference, Pageable pageable);

    @Query(
            value = """
                    select mr.request_id
                    from meal_recipes mr
                    where mr.normalized_source_text = :sourceText
                      and mr.dish_count = :dishCount
                      and ((:totalCalories is null and mr.total_calories is null) or mr.total_calories = :totalCalories)
                      and ((:staple is null and mr.staple is null) or mr.staple = :staple)
                      and ((:locale is null and mr.locale is null) or mr.locale = :locale)
                      and ((:catalogItemId is null and mr.catalog_item_id is null) or mr.catalog_item_id = :catalogItemId)
                    group by mr.request_id
                    order by max(coalesce(mr.updated_at, mr.created_at)) desc, max(mr.id) desc
                    limit 1
                    """,
            nativeQuery = true
    )
    Optional<String> findLatestReusableRequestId(
            @Param("sourceText") String sourceText,
            @Param("dishCount") Integer dishCount,
            @Param("totalCalories") Integer totalCalories,
            @Param("staple") String staple,
            @Param("locale") String locale,
            @Param("catalogItemId") Long catalogItemId
    );

    @EntityGraph(attributePaths = {"catalogItem", "user"})
    List<MealRecipe> findAllByRequestIdOrderByIdAsc(String requestId);

    /**
     * Returns the most recent recipe titles for a user, ordered newest-first.
     * Used to populate the "recently recommended" diversity hint in generation prompts.
     * Call with {@link PageRequest#of(int, int) PageRequest.of(0, limit)}.
     */
    @Query("SELECT r.title FROM MealRecipe r WHERE r.user.id = :userId AND r.title IS NOT NULL AND r.title <> '' ORDER BY r.createdAt DESC")
    List<String> findRecentTitlesByUserId(@Param("userId") Long userId, Pageable pageable);
}
