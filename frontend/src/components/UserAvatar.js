import React from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { buildImageUrl } from '../utils/imageUrl';

const AVATAR_COLORS = ['#6C8EBF', '#D99A9A', '#7FB069', '#A07BEF', '#E09F3E', '#4D908E'];

const hashString = (value = '') => {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31) + value.charCodeAt(index);
  }
  return Math.abs(hash);
};

const getInitials = (name, username) => {
  const source = (name || username || '').trim();
  if (!source) {
    return '?';
  }

  const parts = source.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
  }

  return Array.from(source).slice(0, 2).join('').toUpperCase();
};

const UserAvatar = ({ avatarUrl, name, username, email, size = 40, style, textStyle }) => {
  const uri = buildImageUrl(avatarUrl);

  if (uri) {
    return (
      <Image
        source={{ uri }}
        style={[
          styles.avatar,
          {
            width: size,
            height: size,
            borderRadius: size / 2,
          },
          style,
        ]}
      />
    );
  }

  const seed = [username, name, email].filter(Boolean).join('|');
  const backgroundColor = AVATAR_COLORS[hashString(seed) % AVATAR_COLORS.length];

  return (
    <View
      style={[
        styles.avatar,
        styles.fallbackAvatar,
        {
          width: size,
          height: size,
          borderRadius: size / 2,
          backgroundColor,
        },
        style,
      ]}
    >
      <Text
        style={[
          styles.initials,
          {
            fontSize: Math.max(12, size * 0.36),
          },
          textStyle,
        ]}
      >
        {getInitials(name, username)}
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  avatar: {
    overflow: 'hidden',
  },
  fallbackAvatar: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  initials: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
});

export default UserAvatar;
