import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { Alert, TouchableOpacity } from 'react-native';
import MealFormScreen from '../../src/features/meal/screens/MealFormScreen';
import { mealAPI } from '../../src/features/meal/api';

jest.mock('../../src/features/meal/api', () => ({
  mealAPI: {
    recommendMeals: jest.fn(),
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

describe('MealFormScreen', () => {
  beforeEach(() => {
    jest.spyOn(Alert, 'alert').mockImplementation(() => {});
    mealAPI.recommendMeals.mockReset();
    mealAPI.recommendMeals.mockResolvedValue({
      data: {
        requestId: 'req-1',
        sourceText: '番茄牛腩',
        form: {
          sourceText: '番茄牛腩',
          sourceMode: 'TEXT',
          dishCount: 2,
          totalCalories: 900,
          staple: 'RICE',
          flavor: 'LIGHT',
        },
        provider: 'mock',
        items: [
          {
            id: 1,
            title: '番茄牛腩',
            summary: '酸甜开胃',
            estimatedCalories: 480,
            ingredients: [],
            seasonings: [],
            steps: [],
            imageUrl: '',
            imageStatus: 'OMITTED',
            preference: null,
          },
        ],
        emptyState: null,
      },
    });
  });

  afterEach(() => {
    Alert.alert.mockRestore();
  });

  it('shows validation errors when the form is not within range', async () => {
    const navigation = {
      navigate: jest.fn(),
    };

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <MealFormScreen
          navigation={navigation}
          route={{ params: { sourceText: '番茄牛腩', sourceMode: 'TEXT' } }}
        />
      );
    });

    const dishCountInput = renderer.root.findByProps({ placeholder: '2' });

    await ReactTestRenderer.act(async () => {
      dishCountInput.props.onChangeText('0');
    });

    const submitButton = findTouchableByText(renderer, '生成菜谱');
    await ReactTestRenderer.act(async () => {
      submitButton.props.onPress();
    });

    expect(Alert.alert).toHaveBeenCalledWith('请先完善表单', '几个菜需要在 1 到 6 之间');
    expect(mealAPI.recommendMeals).not.toHaveBeenCalled();
  });

  it('submits the recommendation request with the selected preferences', async () => {
    const navigation = {
      navigate: jest.fn(),
    };

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <MealFormScreen
          navigation={navigation}
          route={{ params: { sourceText: '番茄牛腩', sourceMode: 'VOICE', catalogItemId: 101 } }}
        />
      );
    });

    const sourceInput = renderer.root.findByProps({
      placeholder: '例如：番茄牛腩、鸡腿、菠菜',
    });
    const dishCountInput = renderer.root.findByProps({ placeholder: '2' });
    const calorieInput = renderer.root.findByProps({ placeholder: '900' });

    await ReactTestRenderer.act(async () => {
      sourceInput.props.onChangeText('番茄牛腩');
      dishCountInput.props.onChangeText('3');
      calorieInput.props.onChangeText('1200');
    });

    const noodlesOption = findTouchableByText(renderer, '面条');
    const appetizingOption = findTouchableByText(renderer, '开胃');

    await ReactTestRenderer.act(async () => {
      noodlesOption.props.onPress();
      appetizingOption.props.onPress();
    });

    const submitButton = findTouchableByText(renderer, '生成菜谱');
    await ReactTestRenderer.act(async () => {
      submitButton.props.onPress();
    });

    expect(mealAPI.recommendMeals).toHaveBeenCalledWith(
      expect.objectContaining({
        sourceText: '番茄牛腩',
        sourceMode: 'VOICE',
        catalogItemId: 101,
        dishCount: 3,
        totalCalories: 1200,
        staple: 'NOODLES',
        flavor: 'APPETIZING',
        locale: 'zh-CN',
      })
    );
    expect(navigation.navigate).toHaveBeenCalledWith(
      'MealResults',
      expect.objectContaining({
        form: expect.objectContaining({
          sourceMode: 'VOICE',
          staple: 'NOODLES',
        }),
      })
    );
  });
});
