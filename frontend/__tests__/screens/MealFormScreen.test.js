import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { Alert, TouchableOpacity } from 'react-native';
import MealFormScreen from '../../src/features/meal/screens/MealFormScreen';

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
    expect(navigation.navigate).not.toHaveBeenCalled();
  });

  it('navigates to meal results with the selected preferences and without flavor', async () => {
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

    await ReactTestRenderer.act(async () => {
      noodlesOption.props.onPress();
    });

    const submitButton = findTouchableByText(renderer, '生成菜谱');
    await ReactTestRenderer.act(async () => {
      submitButton.props.onPress();
    });

    expect(navigation.navigate).toHaveBeenCalledWith('MealResults', {
      streamingRequest: {
        sourceText: '番茄牛腩',
        sourceMode: 'VOICE',
        catalogItemId: 101,
        dishCount: 3,
        totalCalories: 1200,
        staple: 'NOODLES',
        locale: 'zh-CN',
      },
      form: {
        sourceText: '番茄牛腩',
        sourceMode: 'VOICE',
        catalogItemId: 101,
        dishCount: 3,
        totalCalories: 1200,
        staple: 'NOODLES',
        locale: 'zh-CN',
      },
    });

    expect(navigation.navigate.mock.calls[0][1].streamingRequest.flavor).toBeUndefined();
  });
});
