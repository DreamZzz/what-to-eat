import React, { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Animated,
  Easing,
  Image,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import Icon from 'react-native-vector-icons/Ionicons';
import { buildImageUrl } from '../../../utils/imageUrl';
import { buildIngredientSummary } from '../utils';
import { mealAPI } from '../api';

const preferenceMeta = {
  LIKE: {
    label: '已喜欢',
    color: '#B85C38',
    icon: 'heart',
  },
  DISLIKE: {
    label: '已讨厌',
    color: '#8A5A44',
    icon: 'close-circle',
  },
};

const ImagePlaceholder = ({ imageStatus }) => {
  const pulseAnim = useRef(new Animated.Value(0.4)).current;

  useEffect(() => {
    if (imageStatus !== 'PENDING') return undefined;
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnim, { toValue: 1, duration: 800, easing: Easing.ease, useNativeDriver: true }),
        Animated.timing(pulseAnim, { toValue: 0.4, duration: 800, easing: Easing.ease, useNativeDriver: true }),
      ])
    );
    loop.start();
    return () => loop.stop();
  }, [imageStatus, pulseAnim]);

  if (imageStatus === 'PENDING') {
    return (
      <Animated.View style={[styles.placeholder, { opacity: pulseAnim }]}>
        <ActivityIndicator size="small" color="#C97C5D" />
        <Text style={styles.placeholderText}>加载图片中…</Text>
      </Animated.View>
    );
  }

  return (
    <View style={styles.placeholder}>
      <Icon name="restaurant-outline" size={28} color="#C97C5D" />
      <Text style={styles.placeholderText}>
        {imageStatus === 'FAILED' ? '图片加载失败' : '暂无图片'}
      </Text>
    </View>
  );
};

const RecipeCard = ({
  recipe,
  compact = false,
  pendingPreference = false,
  onLike,
  onDislike,
}) => {
  const imageUri = buildImageUrl(recipe.imageUrl);
  const currentPreference = recipe.preference;
  const preferenceInfo = currentPreference ? preferenceMeta[currentPreference] : null;
  const { ingredientText, seasoningText } = buildIngredientSummary(recipe);
  const [streamingStepDraft, setStreamingStepDraft] = useState(null);

  // Phase-2 lazy steps loading state
  const [localSteps, setLocalSteps] = useState(recipe.steps || []);
  const [stepsLoading, setStepsLoading] = useState(false);
  const [stepsError, setStepsError] = useState(false);
  const isPendingSteps =
    recipe.stepsStatus === 'PENDING' && localSteps.length === 0 && !stepsLoading && !stepsError;

  useEffect(() => {
    setLocalSteps(recipe.steps || []);
    setStepsError(false);
  }, [recipe.steps]);

  const handleLoadSteps = async () => {
    if (!recipe.id || stepsLoading) return;
    setStepsLoading(true);
    setStepsError(false);
    setStreamingStepDraft(null);
    const accumulating = [];
    try {
      await mealAPI.streamRecipeSteps(recipe.id, {
        onToken: (token) => {
          const nextIndex = token?.index || accumulating.length + 1;
          const nextDelta = token?.contentDelta || '';
          if (!nextDelta) return;
          setStreamingStepDraft((current) => {
            const base = current && current.index === nextIndex
              ? current
              : { index: nextIndex, content: '' };
            return {
              index: nextIndex,
              content: `${base.content}${nextDelta}`,
            };
          });
        },
        onStep: (step) => {
          accumulating.push(step);
          setLocalSteps([...accumulating]);
          setStreamingStepDraft(null);
        },
        onComplete: () => {
          setStreamingStepDraft(null);
          setStepsLoading(false);
        },
        onError: () => {
          setStreamingStepDraft(null);
          setStepsLoading(false);
          setStepsError(true);
        },
      });
    } catch {
      setStepsLoading(false);
      setStepsError(true);
    }
  };

  return (
    <View style={[styles.card, compact && styles.compactCard]}>
      <View style={styles.media}>
        {imageUri ? (
          <Image source={{ uri: imageUri }} style={styles.image} />
        ) : (
          <ImagePlaceholder imageStatus={recipe.imageStatus} />
        )}
        <View style={styles.mediaOverlay}>
          <Text style={styles.calorieTag}>
            {recipe.estimatedCalories ? `${recipe.estimatedCalories} kcal` : '热量待定'}
          </Text>
        </View>
      </View>

      <View style={styles.body}>
        <View style={styles.titleRow}>
          <Text style={styles.title}>{recipe.title}</Text>
          {preferenceInfo ? (
            <View style={[styles.preferenceBadge, { backgroundColor: `${preferenceInfo.color}18` }]}>
              <Icon name={preferenceInfo.icon} size={12} color={preferenceInfo.color} />
              <Text style={[styles.preferenceText, { color: preferenceInfo.color }]}>
                {preferenceInfo.label}
              </Text>
            </View>
          ) : null}
        </View>

        <Text style={styles.summary} numberOfLines={compact ? 3 : 4}>
          {recipe.summary}
        </Text>

        <View style={styles.section}>
          <Text style={styles.sectionLabel}>食材</Text>
          <Text style={styles.sectionValue}>{ingredientText}</Text>
        </View>

        {!compact ? (
          <>
            <View style={styles.section}>
              <Text style={styles.sectionLabel}>佐料</Text>
              <Text style={styles.sectionValue}>{seasoningText}</Text>
            </View>

            <View style={styles.section}>
              <Text style={styles.sectionLabel}>做法</Text>
              {localSteps.length > 0 ? (
                <>
                  {localSteps.map((step, index) => (
                    <Text key={`${recipe.id || recipe.title}-step-${index}`} style={styles.stepLine}>
                      {`${step.index || index + 1}. ${step.content || step}`}
                    </Text>
                  ))}
                  {streamingStepDraft?.content ? (
                    <Text style={[styles.stepLine, styles.stepDraftLine]}>
                      {`${streamingStepDraft.index || localSteps.length + 1}. ${streamingStepDraft.content}`}
                    </Text>
                  ) : null}
                  {stepsLoading ? (
                    <ActivityIndicator size="small" color="#C97C5D" style={styles.stepsLoader} />
                  ) : null}
                </>
              ) : stepsLoading ? (
                <View style={styles.stepsLoadingRow}>
                  <ActivityIndicator size="small" color="#C97C5D" />
                  <Text style={styles.stepsLoadingText}>
                    {streamingStepDraft?.content
                      ? `${streamingStepDraft.index || 1}. ${streamingStepDraft.content}`
                      : '正在加载做法…'}
                  </Text>
                </View>
              ) : isPendingSteps ? (
                <TouchableOpacity onPress={handleLoadSteps} style={styles.loadStepsButton}>
                  <Icon name="book-outline" size={14} color="#B85C38" />
                  <Text style={styles.loadStepsText}>查看做法</Text>
                </TouchableOpacity>
              ) : stepsError ? (
                <TouchableOpacity onPress={handleLoadSteps} style={styles.loadStepsButton}>
                  <Icon name="refresh-outline" size={14} color="#B85C38" />
                  <Text style={styles.loadStepsText}>加载失败，点击重试</Text>
                </TouchableOpacity>
              ) : (
                <Text style={styles.sectionValue}>稍后补充</Text>
              )}
            </View>
          </>
        ) : null}

        {!compact && (onLike || onDislike) ? (
          <View style={styles.actions}>
            <TouchableOpacity
              style={[styles.actionButton, styles.likeButton, currentPreference === 'LIKE' && styles.likeButtonActive]}
              onPress={onLike}
              disabled={pendingPreference}
            >
              {pendingPreference ? (
                <ActivityIndicator size="small" color="#B85C38" />
              ) : (
                <>
                  <Icon name="heart-outline" size={16} color="#B85C38" />
                  <Text style={styles.actionText}>喜欢</Text>
                </>
              )}
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.actionButton, styles.dislikeButton, currentPreference === 'DISLIKE' && styles.dislikeButtonActive]}
              onPress={onDislike}
              disabled={pendingPreference}
            >
              <Icon name="close-outline" size={16} color="#7C4A3A" />
              <Text style={styles.dislikeText}>讨厌</Text>
            </TouchableOpacity>
          </View>
        ) : null}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#FFFDF8',
    borderRadius: 24,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: '#F1E1D0',
    marginBottom: 16,
  },
  compactCard: {
    borderRadius: 20,
  },
  media: {
    height: 180,
    backgroundColor: '#F6E9DF',
  },
  image: {
    width: '100%',
    height: '100%',
  },
  placeholder: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  placeholderText: {
    color: '#9B6A52',
    fontSize: 12,
    fontWeight: '600',
  },
  mediaOverlay: {
    position: 'absolute',
    right: 14,
    bottom: 14,
  },
  calorieTag: {
    backgroundColor: 'rgba(67, 40, 24, 0.82)',
    color: '#FFF8F0',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    fontSize: 12,
    fontWeight: '700',
    overflow: 'hidden',
  },
  body: {
    padding: 16,
    gap: 12,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  title: {
    flex: 1,
    color: '#2B2118',
    fontSize: 20,
    fontWeight: '800',
  },
  preferenceBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
  },
  preferenceText: {
    fontSize: 12,
    fontWeight: '700',
  },
  summary: {
    color: '#5E4A3E',
    fontSize: 14,
    lineHeight: 21,
  },
  section: {
    gap: 4,
  },
  sectionLabel: {
    color: '#9A7A67',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 0.5,
    textTransform: 'uppercase',
  },
  sectionValue: {
    color: '#3D2E24',
    fontSize: 14,
    lineHeight: 20,
  },
  stepLine: {
    color: '#3D2E24',
    fontSize: 14,
    lineHeight: 20,
  },
  stepDraftLine: {
    color: '#9A5A28',
  },
  stepsLoader: {
    alignSelf: 'flex-start',
    marginTop: 4,
  },
  stepsLoadingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  stepsLoadingText: {
    color: '#9A7A67',
    fontSize: 13,
  },
  loadStepsButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    alignSelf: 'flex-start',
    backgroundColor: '#FFF4EB',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 10,
  },
  loadStepsText: {
    color: '#B85C38',
    fontSize: 13,
    fontWeight: '700',
  },
  actions: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 4,
  },
  actionButton: {
    flex: 1,
    borderRadius: 16,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    gap: 6,
  },
  likeButton: {
    backgroundColor: '#F8E8DF',
  },
  likeButtonActive: {
    backgroundColor: '#F0D0C0',
  },
  dislikeButton: {
    backgroundColor: '#F6EFE8',
  },
  dislikeButtonActive: {
    backgroundColor: '#EEDFD5',
  },
  actionText: {
    color: '#B85C38',
    fontSize: 14,
    fontWeight: '700',
  },
  dislikeText: {
    color: '#7C4A3A',
    fontSize: 14,
    fontWeight: '700',
  },
});

export default RecipeCard;
