import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { Text } from 'react-native';
import ProfileScreen from '../../src/screens/ProfileScreen';
import { mealAPI } from '../../src/features/meal/api';

let mockAuthState = {
  user: {
    id: 1,
    username: 'qiang',
    displayName: 'Qiang',
  },
  logout: jest.fn(),
};

jest.mock('../../src/app/providers/AuthContext', () => ({
  useAuth: () => mockAuthState,
}));

jest.mock('../../src/features/meal/api', () => ({
  mealAPI: {
    getFavorites: jest.fn(),
  },
}));

jest.mock('@react-navigation/native', () => {
  const actual = jest.requireActual('@react-navigation/native');
  const mockReact = require('react');
  return {
    ...actual,
    useFocusEffect: (callback) => {
      mockReact.useEffect(() => {
        const cleanup = callback();
        return typeof cleanup === 'function' ? cleanup : undefined;
      }, [callback]);
    },
  };
});

describe('ProfileScreen', () => {
  beforeEach(() => {
    mealAPI.getFavorites.mockReset();
    mockAuthState.logout.mockReset();
  });

  it('shows an empty state when no favorites exist', async () => {
    mealAPI.getFavorites.mockResolvedValue({
      data: {
        items: [],
        pagination: {
          page: 0,
          size: 10,
          totalItems: 0,
          totalPages: 0,
          hasNext: false,
          hasPrevious: false,
        },
        retrieval: {
          scene: 'favorites',
          keyword: null,
          sortStrategy: 'latest',
          provider: 'database',
        },
      },
    });

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(<ProfileScreen />);
    });
    await ReactTestRenderer.act(async () => {
      await Promise.resolve();
    });

    const emptyState = renderer.root
      .findAllByType(Text)
      .find((node) => node.props.children === '还没有喜欢的菜谱');

    expect(emptyState).toBeTruthy();
  });

  it('renders favorite recipes when they exist', async () => {
    mealAPI.getFavorites.mockResolvedValue({
      data: {
        items: [
          {
            id: 1,
            title: '番茄牛腩',
            summary: '酸甜开胃',
            estimatedCalories: 480,
            ingredients: [{ name: '番茄' }, { name: '牛腩' }],
            seasonings: [{ name: '盐' }],
            steps: [],
            imageUrl: '',
            imageStatus: 'OMITTED',
            preference: 'LIKE',
          },
        ],
        pagination: {
          page: 0,
          size: 10,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrevious: false,
        },
        retrieval: {
          scene: 'favorites',
          keyword: null,
          sortStrategy: 'latest',
          provider: 'database',
        },
      },
    });

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(<ProfileScreen />);
    });
    await ReactTestRenderer.act(async () => {
      await Promise.resolve();
    });

    const favoriteTitle = renderer.root
      .findAllByType(Text)
      .find((node) => node.props.children === '番茄牛腩');

    expect(favoriteTitle).toBeTruthy();
  });
});
