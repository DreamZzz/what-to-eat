import React, { useMemo, useState } from 'react';
import {
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import Icon from 'react-native-vector-icons/Ionicons';
import { mealAPI } from '../api';
import { FLAVOR_LABEL_MAP, FLAVOR_OPTIONS, STAPLE_LABEL_MAP, STAPLE_OPTIONS } from '../constants';
import { createMealForm, normalizeRecommendationResponse } from '../utils';
import { getResponseErrorMessage } from '../../../utils/apiError';

const MealFormScreen = ({ navigation, route }) => {
  const initialSourceText = route.params?.sourceText || '';
  const initialSourceMode = route.params?.sourceMode || 'TEXT';
  const catalogItemId = route.params?.catalogItemId ?? null;

  const [sourceText, setSourceText] = useState(initialSourceText);
  const [dishCount, setDishCount] = useState('2');
  const [totalCalories, setTotalCalories] = useState('900');
  const [staple, setStaple] = useState('RICE');
  const [flavor, setFlavor] = useState('LIGHT');
  const [loading, setLoading] = useState(false);

  const formPreview = useMemo(
    () =>
      createMealForm({
        sourceText,
        sourceMode: initialSourceMode,
        dishCount: Number(dishCount) || 0,
        totalCalories: Number(totalCalories) || 0,
        staple,
        flavor,
      }),
    [dishCount, flavor, initialSourceMode, sourceText, staple, totalCalories]
  );

  const validateForm = () => {
    const normalizedText = sourceText.trim();
    const nextDishCount = Number.parseInt(dishCount, 10);
    const nextCalories = Number.parseInt(totalCalories, 10);

    if (!normalizedText) {
      return '请输入菜名、食材或口味偏好';
    }

    if (!Number.isInteger(nextDishCount) || nextDishCount < 1 || nextDishCount > 6) {
      return '几个菜需要在 1 到 6 之间';
    }

    if (!Number.isInteger(nextCalories) || nextCalories < 1 || nextCalories > 5000) {
      return '总热量需要在 1 到 5000 之间';
    }

    return '';
  };

  const handleSubmit = async () => {
    const validationError = validateForm();
    if (validationError) {
      Alert.alert('请先完善表单', validationError);
      return;
    }

    setLoading(true);
    try {
      const payload = {
        sourceText: sourceText.trim(),
        sourceMode: initialSourceMode,
        ...(catalogItemId != null ? { catalogItemId } : {}),
        dishCount: Number.parseInt(dishCount, 10),
        totalCalories: Number.parseInt(totalCalories, 10),
        staple,
        flavor,
        locale: 'zh-CN',
      };

      const response = await mealAPI.recommendMeals(payload);
      const recommendation = normalizeRecommendationResponse(response.data || {});

      navigation.navigate('MealResults', {
        recommendation,
        form: payload,
      });
    } catch (error) {
      console.error('Meal recommendation failed:', error);
      const isUnauthorized = error?.response?.status === 401 || error?.response?.status === 403;
      Alert.alert(
        isUnauthorized ? '登录已失效' : '生成失败',
        getResponseErrorMessage(error, isUnauthorized ? '请重新登录后再试。' : '请稍后重试')
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.card}>
          <View style={styles.headerRow}>
            <View style={styles.badge}>
              <Icon name={initialSourceMode === 'VOICE' ? 'mic' : 'pencil'} size={14} color="#B85C38" />
              <Text style={styles.badgeText}>
                {initialSourceMode === 'VOICE' ? '语音已转写' : '文字输入'}
              </Text>
            </View>
            {catalogItemId != null ? (
              <View style={styles.catalogBadge}>
                <Icon name="library-outline" size={14} color="#B85C38" />
                <Text style={styles.catalogBadgeText}>基础菜单灵感</Text>
              </View>
            ) : null}
            <Text style={styles.title}>补齐偏好</Text>
          </View>

          <Text style={styles.subtitle}>
            你只要给出一个方向，我们负责把它补成一桌能直接下锅的菜。
          </Text>

          <View style={styles.section}>
            <Text style={styles.label}>你想吃什么</Text>
            <TextInput
              value={sourceText}
              onChangeText={setSourceText}
              placeholder="例如：番茄牛腩、鸡腿、菠菜"
              placeholderTextColor="#B8927C"
              style={styles.sourceInput}
              multiline
            />
          </View>

          <View style={styles.row}>
            <View style={styles.column}>
              <Text style={styles.label}>几个菜</Text>
              <TextInput
                value={dishCount}
                onChangeText={setDishCount}
                placeholder="2"
                placeholderTextColor="#B8927C"
                keyboardType="number-pad"
                style={styles.numberInput}
              />
            </View>
            <View style={styles.column}>
              <Text style={styles.label}>总热量</Text>
              <TextInput
                value={totalCalories}
                onChangeText={setTotalCalories}
                placeholder="900"
                placeholderTextColor="#B8927C"
                keyboardType="number-pad"
                style={styles.numberInput}
              />
            </View>
          </View>

          <View style={styles.section}>
            <Text style={styles.label}>主食</Text>
            <View style={styles.optionRow}>
              {STAPLE_OPTIONS.map((option) => (
                <TouchableOpacity
                  key={option.value}
                  style={[styles.optionPill, staple === option.value && styles.optionPillActive]}
                  onPress={() => setStaple(option.value)}
                >
                  <Text
                    style={[
                      styles.optionText,
                      staple === option.value && styles.optionTextActive,
                    ]}
                  >
                    {option.label}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>

          <View style={styles.section}>
            <Text style={styles.label}>口味</Text>
            <View style={styles.optionRow}>
              {FLAVOR_OPTIONS.map((option) => (
                <TouchableOpacity
                  key={option.value}
                  style={[styles.optionPill, flavor === option.value && styles.optionPillActive]}
                  onPress={() => setFlavor(option.value)}
                >
                  <Text
                    style={[
                      styles.optionText,
                      flavor === option.value && styles.optionTextActive,
                    ]}
                  >
                    {option.label}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>

          <View style={styles.previewBox}>
            <Text style={styles.previewLabel}>表单预览</Text>
            <Text style={styles.previewText}>
              {formPreview.dishCount} 道菜 · {formPreview.totalCalories} kcal · {STAPLE_LABEL_MAP[formPreview.staple]} · {FLAVOR_LABEL_MAP[formPreview.flavor]}
            </Text>
          </View>

          <TouchableOpacity
            style={[styles.submitButton, loading && styles.submitButtonDisabled]}
            onPress={handleSubmit}
            disabled={loading}
          >
            <Text style={styles.submitText}>{loading ? '生成中...' : '生成菜谱'}</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFF8F1',
  },
  content: {
    padding: 20,
    paddingBottom: 28,
  },
  card: {
    borderRadius: 30,
    backgroundColor: '#FFFDF9',
    padding: 18,
    borderWidth: 1,
    borderColor: '#F0D8C4',
    gap: 16,
  },
  headerRow: {
    gap: 12,
  },
  badge: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    backgroundColor: '#F8E8DF',
  },
  badgeText: {
    color: '#B85C38',
    fontSize: 12,
    fontWeight: '700',
  },
  catalogBadge: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    backgroundColor: '#F4E5D9',
  },
  catalogBadgeText: {
    color: '#B85C38',
    fontSize: 12,
    fontWeight: '700',
  },
  title: {
    color: '#281B13',
    fontSize: 28,
    fontWeight: '900',
  },
  subtitle: {
    color: '#6E5849',
    fontSize: 15,
    lineHeight: 23,
  },
  section: {
    gap: 8,
  },
  label: {
    color: '#8A6A58',
    fontSize: 13,
    fontWeight: '700',
  },
  sourceInput: {
    minHeight: 100,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: '#E5D1C0',
    backgroundColor: '#FFF9F3',
    paddingHorizontal: 16,
    paddingVertical: 14,
    color: '#2F221A',
    fontSize: 16,
    textAlignVertical: 'top',
  },
  row: {
    flexDirection: 'row',
    gap: 12,
  },
  column: {
    flex: 1,
    gap: 8,
  },
  numberInput: {
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#E5D1C0',
    backgroundColor: '#FFF9F3',
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
    color: '#2F221A',
  },
  optionRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  optionPill: {
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: '#E7C8B1',
    backgroundColor: '#FFF8F1',
  },
  optionPillActive: {
    backgroundColor: '#B85C38',
    borderColor: '#B85C38',
  },
  optionText: {
    color: '#9E6F56',
    fontSize: 13,
    fontWeight: '700',
  },
  optionTextActive: {
    color: '#FFF8F0',
  },
  previewBox: {
    borderRadius: 18,
    backgroundColor: '#FFF4EB',
    padding: 14,
    gap: 4,
  },
  previewLabel: {
    color: '#A06A52',
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  previewText: {
    color: '#2E2018',
    fontSize: 14,
    lineHeight: 20,
  },
  submitButton: {
    minHeight: 54,
    borderRadius: 18,
    backgroundColor: '#B85C38',
    alignItems: 'center',
    justifyContent: 'center',
  },
  submitButtonDisabled: {
    backgroundColor: '#C48B74',
  },
  submitText: {
    color: '#FFF8F0',
    fontSize: 16,
    fontWeight: '800',
  },
});

export default MealFormScreen;
