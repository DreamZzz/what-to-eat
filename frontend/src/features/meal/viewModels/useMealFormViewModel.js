import { useMemo, useState } from 'react';
import { Alert } from 'react-native';
import { createMealForm } from '../utils';
import { STAPLE_LABEL_MAP } from '../constants';

/**
 * ViewModel for MealFormScreen.
 * Owns: all form fields, validation, and submission logic.
 */
export const useMealFormViewModel = (navigation, route) => {
  const initialSourceText = route.params?.sourceText || '';
  const initialSourceMode = route.params?.sourceMode || 'TEXT';
  const catalogItemId = route.params?.catalogItemId ?? null;
  const initialDishCount =
    route.params?.dishCount != null ? String(route.params.dishCount) : '2';

  const [sourceText, setSourceText] = useState(initialSourceText);
  const [dishCount, setDishCount] = useState(initialDishCount);
  const [totalCalories, setTotalCalories] = useState('900');
  const [staple, setStaple] = useState('RICE');

  const formPreview = useMemo(
    () =>
      createMealForm({
        sourceText,
        sourceMode: initialSourceMode,
        dishCount: Number(dishCount) || 0,
        totalCalories: Number(totalCalories) || 0,
        staple,
      }),
    [dishCount, initialSourceMode, sourceText, staple, totalCalories]
  );

  const previewText = useMemo(
    () =>
      `${formPreview.dishCount} 道菜 · ${formPreview.totalCalories} kcal · ${STAPLE_LABEL_MAP[formPreview.staple]}`,
    [formPreview]
  );

  const handleSubmit = () => {
    const normalizedText = sourceText.trim();
    const nextDishCount = Number.parseInt(dishCount, 10);
    const nextCalories = Number.parseInt(totalCalories, 10);

    if (!normalizedText) {
      Alert.alert('请先完善表单', '请输入菜名、食材或口味偏好');
      return;
    }
    if (!Number.isInteger(nextDishCount) || nextDishCount < 1 || nextDishCount > 6) {
      Alert.alert('请先完善表单', '几个菜需要在 1 到 6 之间');
      return;
    }
    if (!Number.isInteger(nextCalories) || nextCalories < 1 || nextCalories > 5000) {
      Alert.alert('请先完善表单', '总热量需要在 1 到 5000 之间');
      return;
    }

    const payload = {
      sourceText: normalizedText,
      sourceMode: initialSourceMode,
      ...(catalogItemId != null ? { catalogItemId } : {}),
      dishCount: nextDishCount,
      totalCalories: nextCalories,
      staple,
      locale: 'zh-CN',
    };

    navigation.navigate('MealResults', { streamingRequest: payload, form: payload });
  };

  return {
    // state
    sourceText,
    dishCount,
    totalCalories,
    staple,
    previewText,
    initialSourceMode,
    catalogItemId,
    // actions
    setSourceText,
    setDishCount,
    setTotalCalories,
    setStaple,
    handleSubmit,
  };
};
