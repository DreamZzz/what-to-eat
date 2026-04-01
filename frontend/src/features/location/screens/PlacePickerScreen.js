import React, { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useHeaderHeight } from '@react-navigation/elements';
import { locationAPI } from '../../../services/api';
import { maybeShowDemoServiceSetupAlert } from '../../../utils/demoServiceSetup';
import { getRequestErrorMessage } from '../../../utils/apiError';
import { buildLocationAddress, buildLocationSelectionParams } from '../../../utils/location';

const PlacePickerScreen = ({ navigation }) => {
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const requestSeqRef = useRef(0);
  const headerHeight = useHeaderHeight();

  useEffect(() => {
    if (!keyword.trim()) {
      requestSeqRef.current += 1;
      setLoading(false);
      setResults([]);
      return undefined;
    }

    const requestSeq = requestSeqRef.current + 1;
    requestSeqRef.current = requestSeq;
    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const response = await locationAPI.search(keyword.trim());
        if (requestSeq !== requestSeqRef.current) {
          return;
        }
        setResults(Array.isArray(response.data) ? response.data : []);
      } catch (error) {
        if (requestSeq !== requestSeqRef.current) {
          return;
        }
        const handled = maybeShowDemoServiceSetupAlert(error?.response?.data, {
          fallbackService: 'map',
        });
        if (!handled) {
          console.warn('Failed to search locations:', error);
          Alert.alert('搜索失败', getRequestErrorMessage(error, '地点搜索失败'));
        }
        setResults([]);
      } finally {
        if (requestSeq === requestSeqRef.current) {
          setLoading(false);
        }
      }
    }, 350);

    return () => clearTimeout(timer);
  }, [keyword]);

  const handleSelect = (item) => {
    navigation.navigate('HomeTabs', {
      screen: 'Create',
      params: buildLocationSelectionParams(item),
    });
  };

  const renderItem = ({ item }) => (
    <TouchableOpacity style={styles.resultItem} onPress={() => handleSelect(item)}>
      <Text style={styles.resultTitle}>{item.name}</Text>
      <Text style={styles.resultSubtitle}>
        {buildLocationAddress(item) || '已记录位置信息'}
      </Text>
    </TouchableOpacity>
  );

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? headerHeight : 0}
    >
      <View style={styles.container}>
        <View style={styles.searchBar}>
          <TextInput
            style={styles.searchInput}
            placeholder="搜索地点、商圈、地标"
            value={keyword}
            onChangeText={setKeyword}
            autoFocus
          />
        </View>

        {loading ? (
          <View style={styles.centerContainer}>
            <ActivityIndicator size="large" color="#6C8EBF" />
          </View>
        ) : (
          <FlatList
            data={results}
            keyExtractor={(item, index) => `${item.name}-${item.longitude}-${index}`}
            renderItem={renderItem}
            contentContainerStyle={styles.listContent}
            ListEmptyComponent={
              <View style={styles.emptyContainer}>
                <Text style={styles.emptyTitle}>{keyword ? '没有匹配地点' : '输入关键词开始搜索'}</Text>
                <Text style={styles.emptySubtitle}>
                  {keyword ? '请尝试更换地点关键字' : '地点搜索使用高德地图 Web API'}
                </Text>
              </View>
            }
          />
        )}
      </View>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F8F9FA',
  },
  searchBar: {
    padding: 16,
    paddingBottom: 8,
  },
  searchInput: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#DEE2E6',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
  },
  listContent: {
    paddingHorizontal: 16,
    paddingBottom: 24,
  },
  resultItem: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#E9ECEF',
    padding: 16,
    marginBottom: 10,
  },
  resultTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212529',
    marginBottom: 4,
  },
  resultSubtitle: {
    fontSize: 13,
    color: '#6C757D',
    lineHeight: 18,
  },
  emptyContainer: {
    alignItems: 'center',
    paddingTop: 80,
    paddingHorizontal: 24,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#495057',
    marginBottom: 8,
  },
  emptySubtitle: {
    fontSize: 14,
    color: '#ADB5BD',
    textAlign: 'center',
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
});

export default PlacePickerScreen;
