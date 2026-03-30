import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { Alert, TouchableOpacity } from 'react-native';
import MealResultsScreen from '../../src/features/meal/screens/MealResultsScreen';
import { mealAPI } from '../../src/features/meal/api';

jest.mock('../../src/features/meal/api', () => ({
  mealAPI: {
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
    mealAPI.updatePreference.mockReset();
    mealAPI.updatePreference.mockResolvedValue({ data: {} });
  });

  afterEach(() => {
    Alert.alert.mockRestore();
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
        preference: null,
      },
    ],
    emptyState: null,
  };

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
