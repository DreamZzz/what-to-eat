import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { Alert, TouchableOpacity } from 'react-native';
import MealResultsScreen from '../../src/features/meal/screens/MealResultsScreen';
import { mealAPI } from '../../src/features/meal/api';

jest.mock('../../src/features/meal/api', () => ({
  mealAPI: {
    streamRecommendations: jest.fn(),
    recommendMeals: jest.fn(),
    fetchRecipeImage: jest.fn(),
    streamRecipeSteps: jest.fn(),
    updatePreference: jest.fn(),
  },
}));

const findTouchableByText = (renderer, label) =>
  renderer.root.findAllByType(TouchableOpacity).find((node) => {
    const walk = (value) => {
      if (typeof value === 'string') {
        return value.includes(label);
      }

      if (Array.isArray(value)) {
        return value.some(walk);
      }

      if (React.isValidElement(value)) {
        return walk(value.props.children);
      }

      return false;
    };

    return walk(node.props.children);
  });

describe('MealResultsScreen', () => {
  beforeEach(() => {
    jest.spyOn(Alert, 'alert').mockImplementation(() => {});
    mealAPI.streamRecommendations.mockReset();
    mealAPI.recommendMeals.mockReset();
    mealAPI.fetchRecipeImage.mockReset();
    mealAPI.streamRecipeSteps.mockReset();
    mealAPI.updatePreference.mockReset();
    mealAPI.streamRecommendations.mockResolvedValue(undefined);
    mealAPI.recommendMeals.mockResolvedValue({
      data: {
        requestId: 'fallback-1',
        sourceText: '川菜',
        items: [],
      },
    });
    mealAPI.fetchRecipeImage.mockResolvedValue({
      data: {
        recipeId: 1,
        imageUrl: 'https://oss.example.com/recipe.jpg',
        imageStatus: 'GENERATED',
      },
    });
    mealAPI.streamRecipeSteps.mockImplementation(async (_recipeId, handlers) => {
      handlers.onStep({ index: 1, content: '焯水备用' });
      handlers.onComplete();
    });
    mealAPI.updatePreference.mockResolvedValue({ data: {} });
  });

  afterEach(() => {
    Alert.alert.mockRestore();
    jest.useRealTimers();
  });

  const recommendation = {
    requestId: 'req-1',
    sourceText: '番茄牛腩',
    form: {
      dishCount: 1,
      totalCalories: 800,
    },
    provider: 'mock',
    items: [
      {
        id: 1,
        title: '番茄牛腩',
        summary: '酸甜开胃',
        estimatedCalories: 480,
        ingredients: [{ name: '番茄' }, { name: '牛腩' }],
        seasonings: [{ name: '盐' }],
        steps: [{ index: 1, content: '焯水' }],
        imageUrl: '',
        imageStatus: 'OMITTED',
        stepsStatus: 'OMITTED',
        preference: null,
      },
    ],
    emptyState: null,
  };

  it('streams recommendations and fetches pending images after completion', async () => {
    mealAPI.streamRecommendations.mockImplementation(async (_payload, handlers) => {
      handlers.onRecipe({
        id: 1,
        title: '鱼香肉丝',
        summary: '酸辣下饭',
        estimatedCalories: 420,
        ingredients: [{ name: '里脊肉' }],
        seasonings: [],
        steps: [],
        imageUrl: '',
        imageStatus: 'PENDING',
        stepsStatus: 'OMITTED',
        preference: null,
      });
      handlers.onComplete();
    });

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <MealResultsScreen
          navigation={{ goBack: jest.fn() }}
          route={{
            params: {
              streamingRequest: {
                sourceText: '川菜',
                sourceMode: 'TEXT',
                dishCount: 1,
                totalCalories: 900,
                staple: 'RICE',
                locale: 'zh-CN',
              },
            },
          }}
        />
      );
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(mealAPI.streamRecommendations).toHaveBeenCalledWith(
      expect.objectContaining({
        sourceText: '川菜',
        dishCount: 1,
      }),
      expect.any(Object)
    );
    expect(mealAPI.fetchRecipeImage).toHaveBeenCalledWith(1);

    const titleNode = renderer.root
      .findAllByType('Text')
      .find((node) => node.props.children === '鱼香肉丝');
    expect(titleNode).toBeTruthy();
  });

  it('stops streaming once the requested dish count has been reached', async () => {
    mealAPI.streamRecommendations.mockImplementation(async (_payload, handlers) => {
      const first = {
        id: 11,
        title: '宫保鸡丁',
        summary: '咸鲜微辣',
        estimatedCalories: 430,
        ingredients: [{ name: '鸡腿肉' }],
        seasonings: [],
        steps: [],
        imageUrl: '',
        imageStatus: 'PENDING',
        stepsStatus: 'OMITTED',
        preference: null,
      };
      const second = {
        id: 12,
        title: '鱼香肉丝',
        summary: '酸甜下饭',
        estimatedCalories: 390,
        ingredients: [{ name: '里脊肉' }],
        seasonings: [],
        steps: [],
        imageUrl: '',
        imageStatus: 'PENDING',
        stepsStatus: 'OMITTED',
        preference: null,
      };

      handlers.onRecipe(first);
      handlers.onRecipe(second);
      if (handlers.shouldStop(second)) {
        handlers.onComplete();
      }
    });

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <MealResultsScreen
          navigation={{ goBack: jest.fn() }}
          route={{
            params: {
              streamingRequest: {
                sourceText: '川菜',
                sourceMode: 'TEXT',
                dishCount: 2,
                totalCalories: 900,
                staple: 'RICE',
                locale: 'zh-CN',
              },
            },
          }}
        />
      );
      await Promise.resolve();
      await Promise.resolve();
    });

    const streamingText = renderer.root
      .findAllByType('Text')
      .find((node) =>
        typeof node.props.children === 'string'
          && node.props.children.includes('继续生成中')
      );

    expect(streamingText).toBeUndefined();
    expect(mealAPI.fetchRecipeImage).toHaveBeenCalledWith(11);
    expect(mealAPI.fetchRecipeImage).toHaveBeenCalledWith(12);
  });

  it('falls back to the sync recommendation endpoint when the stream stays silent', async () => {
    jest.useFakeTimers();
    mealAPI.streamRecommendations.mockImplementation(async () => {
      await Promise.resolve();
    });
    mealAPI.recommendMeals.mockResolvedValue({
      data: {
        requestId: 'fallback-2',
        sourceText: '川菜',
        form: {
          dishCount: 1,
          totalCalories: 900,
          staple: 'RICE',
        },
        items: [
          {
            id: 21,
            title: '宫保鸡丁',
            summary: '酸甜微辣，下饭稳定',
            estimatedCalories: 460,
            ingredients: [{ name: '鸡腿肉' }],
            seasonings: [],
            steps: [],
            imageUrl: '',
            imageStatus: 'OMITTED',
            stepsStatus: 'OMITTED',
            preference: null,
          },
        ],
      },
    });

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <MealResultsScreen
          navigation={{ goBack: jest.fn() }}
          route={{
            params: {
              streamingRequest: {
                sourceText: '川菜',
                sourceMode: 'TEXT',
                dishCount: 1,
                totalCalories: 900,
                staple: 'RICE',
                locale: 'zh-CN',
              },
            },
          }}
        />
      );
      await Promise.resolve();
      await Promise.resolve();
    });

    await ReactTestRenderer.act(async () => {
      jest.advanceTimersByTime(12000);
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(mealAPI.recommendMeals).toHaveBeenCalledWith(
      expect.objectContaining({ sourceText: '川菜' })
    );

    const titleNode = renderer.root
      .findAllByType('Text')
      .find((node) => node.props.children === '宫保鸡丁');
    expect(titleNode).toBeTruthy();
  });

  it('falls back immediately when the stream completes without any recipe events', async () => {
    mealAPI.streamRecommendations.mockImplementation(async (_payload, handlers) => {
      handlers.onComplete();
    });
    mealAPI.recommendMeals.mockResolvedValue({
      data: {
        requestId: 'fallback-3',
        sourceText: '川菜',
        form: {
          dishCount: 1,
          totalCalories: 900,
          staple: 'RICE',
        },
        items: [
          {
            id: 31,
            title: '鱼香肉丝',
            summary: '咸鲜微甜，下饭稳定',
            estimatedCalories: 430,
            ingredients: [{ name: '里脊肉' }],
            seasonings: [],
            steps: [],
            imageUrl: '',
            imageStatus: 'OMITTED',
            stepsStatus: 'OMITTED',
            preference: null,
          },
        ],
      },
    });

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <MealResultsScreen
          navigation={{ goBack: jest.fn() }}
          route={{
            params: {
              streamingRequest: {
                sourceText: '川菜',
                sourceMode: 'TEXT',
                dishCount: 1,
                totalCalories: 900,
                staple: 'RICE',
                locale: 'zh-CN',
              },
            },
          }}
        />
      );
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(mealAPI.recommendMeals).toHaveBeenCalledWith(
      expect.objectContaining({ sourceText: '川菜' })
    );

    const titleNode = renderer.root
      .findAllByType('Text')
      .find((node) => node.props.children === '鱼香肉丝');
    expect(titleNode).toBeTruthy();
  });

  it('loads recipe steps lazily when the user asks to view them', async () => {
    const pendingStepsRecommendation = {
      ...recommendation,
      items: [
        {
          ...recommendation.items[0],
          steps: [],
          stepsStatus: 'PENDING',
        },
      ],
    };

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <MealResultsScreen
          navigation={{ goBack: jest.fn() }}
          route={{ params: { recommendation: pendingStepsRecommendation } }}
        />
      );
    });

    const loadStepsButton = findTouchableByText(renderer, '查看做法');
    let releaseStream;
    mealAPI.streamRecipeSteps.mockImplementation(
      async (_recipeId, handlers) =>
        new Promise((resolve) => {
          handlers.onToken({ index: 1, contentDelta: '焯' });
          handlers.onToken({ index: 1, contentDelta: '水备' });
          releaseStream = () => {
            handlers.onToken({ index: 1, contentDelta: '用' });
            handlers.onStep({ index: 1, content: '焯水备用' });
            handlers.onComplete();
            resolve();
          };
        })
    );

    await ReactTestRenderer.act(async () => {
      loadStepsButton.props.onPress();
      await Promise.resolve();
    });

    const draftNode = renderer.root
      .findAllByType('Text')
      .find((node) => node.props.children === '1. 焯水备');
    expect(draftNode).toBeTruthy();

    await ReactTestRenderer.act(async () => {
      releaseStream();
      await Promise.resolve();
    });

    expect(mealAPI.streamRecipeSteps).toHaveBeenCalledWith(1, expect.any(Object));

    const stepNode = renderer.root
      .findAllByType('Text')
      .find((node) => node.props.children === '1. 焯水备用');
    expect(stepNode).toBeTruthy();
  });

  it('optimistically marks a recipe as liked', async () => {
    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <MealResultsScreen
          navigation={{ goBack: jest.fn() }}
          route={{ params: { recommendation } }}
        />
      );
    });

    const likeButton = findTouchableByText(renderer, '喜欢');
    await ReactTestRenderer.act(async () => {
      likeButton.props.onPress();
    });

    expect(mealAPI.updatePreference).toHaveBeenCalledWith(1, 'LIKE');

    const likedBadge = renderer.root
      .findAllByType('Text')
      .find((node) => node.props.children === '已喜欢');

    expect(likedBadge).toBeTruthy();
  });

  it('optimistically marks a recipe as disliked', async () => {
    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <MealResultsScreen
          navigation={{ goBack: jest.fn() }}
          route={{ params: { recommendation } }}
        />
      );
    });

    const dislikeButton = findTouchableByText(renderer, '讨厌');
    await ReactTestRenderer.act(async () => {
      dislikeButton.props.onPress();
    });

    expect(mealAPI.updatePreference).toHaveBeenCalledWith(1, 'DISLIKE');

    const dislikedBadge = renderer.root
      .findAllByType('Text')
      .find((node) => node.props.children === '已讨厌');

    expect(dislikedBadge).toBeTruthy();
  });
});
