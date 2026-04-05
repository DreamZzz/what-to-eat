import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { Text } from 'react-native';
import RecipeDetailScreen from '../../src/features/meal/screens/RecipeDetailScreen';
import { mealAPI } from '../../src/features/meal/api';

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 24, bottom: 12, left: 0, right: 0 }),
}));

jest.mock('../../src/features/meal/api', () => ({
  mealAPI: {
    getRecipe: jest.fn(),
    fetchRecipeImage: jest.fn(),
    streamRecipeSteps: jest.fn(),
  },
}));

describe('RecipeDetailScreen', () => {
  beforeEach(() => {
    mealAPI.getRecipe.mockReset();
    mealAPI.fetchRecipeImage.mockReset();
    mealAPI.streamRecipeSteps.mockReset();
  });

  it('loads recipe detail and hydrates pending image and steps', async () => {
    mealAPI.getRecipe.mockResolvedValue({
      data: {
        id: 7,
        title: '红烧鸡翅',
        summary: '咸香下饭',
        estimatedCalories: 460,
        ingredients: [{ name: '鸡翅中' }],
        seasonings: [{ name: '生抽' }],
        steps: [],
        imageUrl: '',
        imageStatus: 'PENDING',
        stepsStatus: 'PENDING',
      },
    });
    mealAPI.fetchRecipeImage.mockResolvedValue({
      data: {
        recipeId: 7,
        imageUrl: 'https://oss.example.com/wings.jpg',
        imageStatus: 'GENERATED',
      },
    });
    mealAPI.streamRecipeSteps.mockImplementation(async (_recipeId, handlers) => {
      handlers.onStep({ index: 1, content: '鸡翅煎至两面金黄。' });
      handlers.onComplete();
    });

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <RecipeDetailScreen route={{ params: { recipeId: 7 } }} />
      );
    });
    await ReactTestRenderer.act(async () => {
      await Promise.resolve();
    });
    await ReactTestRenderer.act(async () => {
      await Promise.resolve();
    });

    expect(mealAPI.getRecipe).toHaveBeenCalledWith(7);
    expect(mealAPI.fetchRecipeImage).toHaveBeenCalledWith(7);
    expect(mealAPI.streamRecipeSteps).toHaveBeenCalledWith(7, expect.any(Object));

    const titleNode = renderer.root
      .findAllByType(Text)
      .find((node) => node.props.children === '红烧鸡翅');
    const stepNode = renderer.root
      .findAllByType(Text)
      .find((node) => node.props.children === '1. 鸡翅煎至两面金黄。');

    expect(titleNode).toBeTruthy();
    expect(stepNode).toBeTruthy();
  });
});
