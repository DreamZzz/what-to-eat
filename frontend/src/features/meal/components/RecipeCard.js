import React from 'react';
import {
  ActivityIndicator,
  Image,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import Icon from 'react-native-vector-icons/Ionicons';
import { buildImageUrl } from '../../../utils/imageUrl';
import { buildIngredientSummary } from '../utils';

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

  return (
    <View style={[styles.card, compact && styles.compactCard]}>
      <View style={styles.media}>
        {imageUri ? (
          <Image source={{ uri: imageUri }} style={styles.image} />
        ) : (
          <View style={styles.placeholder}>
            <Icon name="restaurant-outline" size={28} color="#C97C5D" />
            <Text style={styles.placeholderText}>
              {recipe.imageStatus === 'FAILED' ? '图片生成失败' : '等待图片'}
            </Text>
          </View>
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
              {recipe.steps.length > 0 ? (
                recipe.steps.slice(0, 4).map((step, index) => (
                  <Text key={`${recipe.id || recipe.title}-step-${index}`} style={styles.stepLine}>
                    {`${step.index || index + 1}. ${step.content || step}`}
                  </Text>
                ))
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
