import React, { useMemo, useRef, useState } from 'react';
import {
  Alert,
  Keyboard,
  KeyboardAvoidingView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  TouchableWithoutFeedback,
  View,
  Platform,
} from 'react-native';
import { useHeaderHeight } from '@react-navigation/elements';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useAuth } from '../context/AuthContext';
import { userAPI } from '../services/api';
import { getRequestErrorMessage } from '../utils/apiError';

const GENDER_OPTIONS = ['保密', '男', '女', '其他'];

const formatDateInput = (value) => value.replace(/[^\d-]/g, '').slice(0, 10);

const EditProfileScreen = ({ navigation, route }) => {
  const initialUser = route.params?.user || {};
  const { updateUser } = useAuth();
  const [displayName, setDisplayName] = useState(initialUser.displayName || initialUser.username || '');
  const [phone, setPhone] = useState(initialUser.phone || '');
  const [bio, setBio] = useState(initialUser.bio || '');
  const [gender, setGender] = useState(initialUser.gender || '保密');
  const [birthday, setBirthday] = useState(initialUser.birthday || '');
  const [region, setRegion] = useState(initialUser.region || '');
  const [saving, setSaving] = useState(false);
  const bioInputRef = useRef(null);
  const headerHeight = useHeaderHeight();
  const insets = useSafeAreaInsets();

  const userId = initialUser.id;

  const payload = useMemo(
    () => ({
      displayName: displayName.trim(),
      phone: phone.trim(),
      bio: bio.trim(),
      gender,
      birthday: birthday.trim() || null,
      region: region.trim(),
    }),
    [bio, birthday, displayName, gender, phone, region]
  );

  const handleSave = async () => {
    if (!userId) {
      Alert.alert('保存失败', '无法识别用户信息');
      return;
    }

    if (!payload.displayName) {
      Alert.alert('保存失败', '名字不能为空');
      return;
    }

    if (payload.birthday && !/^\d{4}-\d{2}-\d{2}$/.test(payload.birthday)) {
      Alert.alert('保存失败', '生日格式应为 YYYY-MM-DD');
      return;
    }

    setSaving(true);
    try {
      const response = await userAPI.updateProfile(userId, payload);
      await updateUser(response.data);
      navigation.goBack();
    } catch (error) {
      console.error('Failed to update profile:', error);
      Alert.alert('保存失败', getRequestErrorMessage(error, '保存失败，请稍后重试'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? headerHeight : 0}
    >
      <TouchableWithoutFeedback onPress={Keyboard.dismiss} accessible={false}>
        <ScrollView
          style={styles.container}
          contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="interactive"
          contentInsetAdjustmentBehavior="always"
        >
      <View style={styles.section}>
        <Text style={styles.label}>名字</Text>
        <TextInput
          style={styles.input}
          placeholder="输入展示给别人的名字"
          value={displayName}
          onChangeText={setDisplayName}
        />
      </View>

      <View style={styles.section}>
        <Text style={styles.label}>一句话自我介绍</Text>
        <TextInput
          ref={bioInputRef}
          style={[styles.input, styles.multilineInput]}
          placeholder="介绍一下自己"
          defaultValue={bio}
          onChangeText={setBio}
          multiline
          autoCorrect={false}
          scrollEnabled
        />
      </View>

      <View style={styles.section}>
        <Text style={styles.label}>手机号</Text>
        <TextInput
          style={styles.input}
          placeholder="绑定后可用短信验证码登录"
          value={phone}
          onChangeText={setPhone}
          keyboardType="phone-pad"
        />
      </View>

      <View style={styles.section}>
        <Text style={styles.label}>性别</Text>
        <View style={styles.optionRow}>
          {GENDER_OPTIONS.map((option) => (
            <TouchableOpacity
              key={option}
              style={[styles.optionButton, gender === option && styles.optionButtonActive]}
              onPress={() => setGender(option)}
            >
              <Text style={[styles.optionText, gender === option && styles.optionTextActive]}>
                {option}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.label}>生日</Text>
        <TextInput
          style={styles.input}
          placeholder="YYYY-MM-DD"
          value={birthday}
          onChangeText={(text) => setBirthday(formatDateInput(text))}
          keyboardType="numbers-and-punctuation"
        />
      </View>

      <View style={styles.section}>
        <Text style={styles.label}>地区</Text>
        <TextInput
          style={styles.input}
          placeholder="例如：北京 / 上海 / 杭州"
          value={region}
          onChangeText={setRegion}
        />
      </View>

      <TouchableOpacity
        style={[styles.saveButton, saving && styles.saveButtonDisabled]}
        disabled={saving}
        onPress={handleSave}
      >
        <Text style={styles.saveButtonText}>{saving ? '保存中...' : '保存资料'}</Text>
      </TouchableOpacity>
        </ScrollView>
      </TouchableWithoutFeedback>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F8F9FA',
  },
  content: {
    padding: 20,
  },
  section: {
    marginBottom: 20,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: '#495057',
    marginBottom: 8,
  },
  input: {
    backgroundColor: 'white',
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#DEE2E6',
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
    color: '#212529',
  },
  multilineInput: {
    minHeight: 110,
    textAlignVertical: 'top',
  },
  optionRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  optionButton: {
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 20,
    backgroundColor: 'white',
    borderWidth: 1,
    borderColor: '#DEE2E6',
  },
  optionButtonActive: {
    backgroundColor: '#6C8EBF',
    borderColor: '#6C8EBF',
  },
  optionText: {
    color: '#495057',
    fontSize: 14,
    fontWeight: '500',
  },
  optionTextActive: {
    color: 'white',
  },
  saveButton: {
    marginTop: 10,
    backgroundColor: '#6C8EBF',
    borderRadius: 12,
    paddingVertical: 15,
    alignItems: 'center',
  },
  saveButtonDisabled: {
    backgroundColor: '#ADB5BD',
  },
  saveButtonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: '600',
  },
});

export default EditProfileScreen;
