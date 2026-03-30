import {
  buildLocationAddress,
  buildLocationSelectionParams,
  normalizeLocationSelection,
} from '../../src/utils/location';

describe('location utils', () => {
  it('builds a compact address string from amap fields', () => {
    expect(
      buildLocationAddress({
        city: '北京市',
        district: '朝阳区',
        address: '三里屯路11号',
      })
    ).toBe('北京市 朝阳区 三里屯路11号');
  });

  it('normalizes selected locations into post payload shape', () => {
    expect(
      normalizeLocationSelection({
        name: '三里屯太古里',
        city: '北京市',
        district: '朝阳区',
        address: '三里屯路11号',
        latitude: '39.934871',
        longitude: '116.45399',
      })
    ).toEqual({
      name: '三里屯太古里',
      address: '北京市 朝阳区 三里屯路11号',
      latitude: 39.934871,
      longitude: 116.45399,
    });
  });

  it('drops invalid selections without a name', () => {
    expect(
      normalizeLocationSelection({
        city: '北京市',
      })
    ).toBeNull();
  });

  it('adds a token so repeated selections still propagate through navigation params', () => {
    const params = buildLocationSelectionParams({
      name: '三里屯',
      district: '朝阳区',
      address: '三里屯路',
      latitude: '39.93',
      longitude: '116.45',
    });

    expect(params.selectedLocation).toEqual({
      name: '三里屯',
      address: '朝阳区 三里屯路',
      latitude: 39.93,
      longitude: 116.45,
    });
    expect(typeof params.selectedLocationToken).toBe('string');
    expect(params.selectedLocationToken.length).toBeGreaterThan(0);
  });
});
