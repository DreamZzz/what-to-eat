import { getRequestErrorMessage, getResponseErrorMessage } from '../../src/utils/apiError';

describe('api error utils', () => {
  it('prefers backend string error bodies', () => {
    expect(
      getResponseErrorMessage(
        {
          response: {
            data: '手机号未绑定',
          },
        },
        '默认错误'
      )
    ).toBe('手机号未绑定');
  });

  it('uses backend message fields when present', () => {
    expect(
      getResponseErrorMessage(
        {
          response: {
            data: {
              message: '短信发送失败',
            },
          },
        },
        '默认错误'
      )
    ).toBe('短信发送失败');
  });

  it('builds readable network failures with request metadata', () => {
    expect(
      getRequestErrorMessage(
        {
          message: 'timeout of 15000ms exceeded',
          code: 'ECONNABORTED',
        },
        '注册失败',
        {
          apiBaseUrl: 'http://127.0.0.1:8080/api',
          includeRequestUrl: true,
          includeErrorCode: true,
          networkFallbackMessage: '无法连接到后端服务',
        }
      )
    ).toBe(
      '无法连接到后端服务\n请求地址: http://127.0.0.1:8080/api\n错误信息: timeout of 15000ms exceeded\n错误代码: ECONNABORTED'
    );
  });
});
