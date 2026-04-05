import AsyncStorage from '@react-native-async-storage/async-storage';
import { mealAPI } from '../../src/features/meal/api';

jest.mock('@react-native-async-storage/async-storage', () => ({
  getItem: jest.fn(),
  multiRemove: jest.fn(),
}));

jest.mock('../../src/app/config/api', () => ({
  API_BASE_URL: 'https://eat.868299.com/api',
}));

describe('mealAPI.streamRecipeSteps', () => {
  const originalXmlHttpRequest = global.XMLHttpRequest;

  afterEach(() => {
    global.XMLHttpRequest = originalXmlHttpRequest;
    jest.clearAllMocks();
  });

  it('completes immediately when the backend emits an explicit done event', async () => {
    const events = [];
    const completions = [];

    AsyncStorage.getItem.mockResolvedValue('token-123');

    class MockXMLHttpRequest {
      constructor() {
        this.responseText = '';
        this.headers = {};
        this.status = 200;
      }

      open(method, url) {
        this.method = method;
        this.url = url;
      }

      setRequestHeader(name, value) {
        this.headers[name] = value;
      }

      send() {
        this.responseText = [
          'event: token',
          'data: {"index":1,"contentDelta":"焯水"}',
          '',
          'event: step',
          'data: {"index":1,"content":"焯水备用"}',
          '',
          'event: done',
          'data: {"complete":true}',
          '',
        ].join('\n');

        this.onprogress?.();
      }
    }

    global.XMLHttpRequest = MockXMLHttpRequest;

    await mealAPI.streamRecipeSteps(1, {
      onToken: (payload) => events.push(['token', payload]),
      onStep: (payload) => events.push(['step', payload]),
      onComplete: () => completions.push('done'),
      onError: (error) => {
        throw error;
      },
    });

    expect(events).toEqual([
      ['token', { index: 1, contentDelta: '焯水' }],
      ['step', { index: 1, content: '焯水备用' }],
    ]);
    expect(completions).toEqual(['done']);
  });
});

describe('mealAPI.streamRecommendations', () => {
  const originalXmlHttpRequest = global.XMLHttpRequest;

  afterEach(() => {
    global.XMLHttpRequest = originalXmlHttpRequest;
    jest.clearAllMocks();
  });

  it('completes immediately when the backend emits an explicit done event', async () => {
    const recipes = [];
    const summaries = [];
    const completions = [];

    AsyncStorage.getItem.mockResolvedValue('token-123');

    class MockXMLHttpRequest {
      constructor() {
        this.responseText = '';
        this.headers = {};
        this.status = 200;
      }

      open(method, url) {
        this.method = method;
        this.url = url;
      }

      setRequestHeader(name, value) {
        this.headers[name] = value;
      }

      abort() {}

      send() {
        this.responseText = [
          'event: summary',
          'data: {"reasonSummary":"这几道菜更适合川味家常搭配"}',
          '',
          'event: recipe',
          'data: {"id":1,"title":"鱼香肉丝"}',
          '',
          'event: done',
          'data: {"complete":true}',
          '',
        ].join('\n');

        this.onprogress?.();
      }
    }

    global.XMLHttpRequest = MockXMLHttpRequest;

    await mealAPI.streamRecommendations(
      { sourceText: '川菜', dishCount: 1 },
      {
        onSummary: (payload) => summaries.push(payload),
        onRecipe: (payload) => recipes.push(payload),
        onComplete: () => completions.push('done'),
        onError: (error) => {
          throw error;
        },
      }
    );

    expect(summaries).toEqual(['这几道菜更适合川味家常搭配']);
    expect(recipes).toEqual([{ id: 1, title: '鱼香肉丝' }]);
    expect(completions).toEqual(['done']);
  });
});
