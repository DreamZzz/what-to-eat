import React from 'react';
import {
  Image,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/Ionicons';
import { INPUT_MODES } from '../constants';
import { useMealHomeViewModel } from '../viewModels/useMealHomeViewModel';

const HomeScreen = ({ navigation }) => {
  const insets = useSafeAreaInsets();
  const vm = useMealHomeViewModel(navigation);

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={[
          styles.content,
          {
            paddingTop: Math.max(insets.top + 10, 24),
            paddingBottom: Math.max(insets.bottom + 28, 36),
          },
        ]}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        <View style={styles.hero}>
          <View style={styles.brandRow}>
            <View style={styles.logoWrap}>
              <Image
                source={require('../../../../assets/branding/app-icon-1024.png')}
                style={styles.logo}
              />
            </View>
            <View style={styles.brandCopy}>
              <Text style={styles.brand}>What To Eat</Text>
              <Text style={styles.kicker}>让今天这顿饭更快开始</Text>
            </View>
          </View>

          <Text style={styles.headline}>今天想吃点什么</Text>
          <Text style={styles.supporting}>
            输入菜名、食材或直接说出来，我们帮你补齐菜数、热量和主食偏好。
          </Text>

          <View style={styles.inspirationModule}>
            <View style={styles.inspirationCopy}>
              <Text style={styles.inspirationEyebrow}>没想法时</Text>
              <Text style={styles.inspirationTitle}>来点灵感</Text>
            </View>
            <TouchableOpacity
              testID="home-inspiration-button"
              style={styles.inspirationButton}
              onPress={vm.handleSuggestion}
              disabled={vm.transcribing}
            >
              <Text style={styles.inspirationButtonText}>来点灵感</Text>
              <Icon name="sparkles-outline" size={18} color="#FFF8F0" />
            </TouchableOpacity>
          </View>

          <Text style={styles.catalogStatusText}>{vm.statusText}</Text>
        </View>

        <View style={styles.inputPanel}>
          <View style={styles.inputHeader}>
            <Text style={styles.inputTitle}>输入你现在的方向</Text>
          </View>

          <View style={styles.composerRow}>
            <TouchableOpacity
              testID="home-mode-toggle"
              style={[
                styles.modeToggle,
                vm.inputMode === INPUT_MODES.VOICE && styles.modeToggleActive,
              ]}
              onPress={vm.handleModeToggle}
            >
              <Icon
                name={vm.inputMode === INPUT_MODES.VOICE ? 'mic-outline' : 'pencil-outline'}
                size={22}
                color={vm.inputMode === INPUT_MODES.VOICE ? '#FFF8F0' : '#B85C38'}
              />
              <Text
                style={[
                  styles.modeToggleLabel,
                  vm.inputMode === INPUT_MODES.VOICE && styles.modeToggleLabelActive,
                ]}
              >
                {vm.inputMode === INPUT_MODES.VOICE ? '语音' : '文字'}
              </Text>
            </TouchableOpacity>

            {vm.inputMode === INPUT_MODES.TEXT ? (
              <TextInput
                testID="home-text-input"
                value={vm.draftText}
                onChangeText={(value) => {
                  vm.setDraftText(value);
                  vm.setSourceOrigin(INPUT_MODES.TEXT);
                }}
                placeholder="菜名、食材或口味方向…"
                placeholderTextColor="#B8927C"
                multiline={false}
                returnKeyType="send"
                blurOnSubmit
                onSubmitEditing={vm.submitDraft}
                style={styles.textInput}
              />
            ) : (
              <TouchableOpacity
                testID="home-voice-button"
                style={[
                  styles.voiceButton,
                  vm.recording && styles.voiceButtonActive,
                  vm.transcribing && styles.voiceButtonDisabled,
                ]}
                onLongPress={vm.handleVoiceRecordStart}
                onPressOut={vm.finishVoiceCapture}
                delayLongPress={180}
                disabled={vm.transcribing}
              >
                <Icon
                  name={vm.recording ? 'radio-button-on-outline' : 'mic-circle-outline'}
                  size={22}
                  color="#FFF8F0"
                />
                <Text style={styles.voiceButtonText}>
                  {vm.transcribing ? '转写中…' : vm.recording ? '松手结束' : '长按录制'}
                </Text>
              </TouchableOpacity>
            )}
          </View>

          <Text style={styles.helperText}>{vm.helperText}</Text>

          {vm.lastTranscript ? (
            <View style={styles.transcriptBox}>
              <Text style={styles.transcriptLabel}>最近一次识别</Text>
              <Text style={styles.transcriptText}>{vm.lastTranscript}</Text>
            </View>
          ) : null}
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
    paddingHorizontal: 20,
    gap: 18,
  },
  hero: {
    backgroundColor: '#FFF5EB',
    borderWidth: 1,
    borderColor: '#F2D4BE',
    borderRadius: 30,
    padding: 14,
    gap: 10,
    shadowColor: '#C99A73',
    shadowOpacity: 0.12,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: 10 },
  },
  brandRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  logoWrap: {
    width: 70,
    height: 70,
    borderRadius: 22,
    backgroundColor: '#FFFDF9',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#C9895B',
    shadowOpacity: 0.18,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 5 },
  },
  logo: {
    width: 54,
    height: 54,
    borderRadius: 18,
  },
  brandCopy: {
    flex: 1,
    gap: 4,
  },
  brand: {
    color: '#3A281C',
    fontSize: 24,
    fontWeight: '900',
    letterSpacing: 0.3,
  },
  kicker: {
    color: '#8F6B57',
    fontSize: 13,
    fontWeight: '600',
  },
  headline: {
    color: '#2A1B13',
    fontSize: 30,
    fontWeight: '900',
    lineHeight: 36,
  },
  supporting: {
    color: '#6E5849',
    fontSize: 15,
    lineHeight: 23,
  },
  inspirationModule: {
    borderRadius: 18,
    backgroundColor: '#FFFDF9',
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderWidth: 1,
    borderColor: '#F0D8C4',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  inspirationCopy: {
    flex: 1,
    gap: 2,
  },
  inspirationEyebrow: {
    color: '#AA6B47',
    fontSize: 11,
    fontWeight: '800',
  },
  inspirationTitle: {
    color: '#2A1B13',
    fontSize: 17,
    fontWeight: '900',
  },
  inspirationButton: {
    minHeight: 44,
    paddingHorizontal: 16,
    borderRadius: 14,
    backgroundColor: '#B85C38',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  inspirationButtonText: {
    color: '#FFF8F0',
    fontSize: 14,
    fontWeight: '800',
  },
  inputPanel: {
    backgroundColor: '#FFFDF9',
    borderRadius: 24,
    padding: 14,
    borderWidth: 1,
    borderColor: '#F0D8C4',
    gap: 12,
  },
  inputHeader: {},
  inputTitle: {
    color: '#2A1B13',
    fontSize: 18,
    fontWeight: '900',
  },
  composerRow: {
    flexDirection: 'row',
    alignItems: 'stretch',
    gap: 10,
  },
  modeToggle: {
    width: 56,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#E7C8B1',
    backgroundColor: '#FFF8F1',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    paddingVertical: 10,
  },
  modeToggleActive: {
    backgroundColor: '#B85C38',
    borderColor: '#B85C38',
  },
  modeToggleLabel: {
    color: '#7B513C',
    fontSize: 11,
    fontWeight: '800',
  },
  modeToggleLabelActive: {
    color: '#FFF8F0',
  },
  textInput: {
    flex: 1,
    minHeight: 52,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#E5D1C0',
    backgroundColor: '#FFFDF9',
    paddingHorizontal: 14,
    paddingVertical: 14,
    color: '#2F221A',
    fontSize: 15,
  },
  helperText: {
    color: '#A07C66',
    fontSize: 12,
    lineHeight: 18,
  },
  voiceButton: {
    flex: 1,
    minHeight: 52,
    borderRadius: 16,
    backgroundColor: '#B85C38',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingHorizontal: 14,
  },
  voiceButtonActive: {
    backgroundColor: '#7C3E28',
  },
  voiceButtonDisabled: {
    opacity: 0.76,
  },
  voiceButtonText: {
    color: '#FFF8F0',
    fontSize: 15,
    fontWeight: '800',
  },
  transcriptBox: {
    borderRadius: 20,
    padding: 14,
    backgroundColor: '#FFF4EB',
    gap: 6,
  },
  transcriptLabel: {
    color: '#9C6B54',
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  transcriptText: {
    color: '#2E2018',
    fontSize: 15,
    lineHeight: 22,
  },
  catalogStatusText: {
    color: '#7A6052',
    fontSize: 12,
    lineHeight: 18,
  },
});

export default HomeScreen;
