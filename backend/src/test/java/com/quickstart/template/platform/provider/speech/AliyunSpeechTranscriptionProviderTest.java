package com.quickstart.template.platform.provider.speech;

import com.quickstart.template.contexts.meal.application.SpeechTranscriptionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliyunSpeechTranscriptionProviderTest {

    @Test
    @DisplayName("normalizeWavToAliyunPreferredFormat should convert wav into 16k mono PCM")
    void normalizeWavToAliyunPreferredFormat_ShouldConvertToAliyunPreferredWav() throws Exception {
        byte[] stereoWav = buildSilentWav(
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44_100, 16, 2, 4, 44_100, false),
                4_410
        );

        byte[] normalized = AliyunSpeechTranscriptionProvider.normalizeWavToAliyunPreferredFormat(stereoWav);

        assertTrue(normalized.length > 44);
        assertEquals('R', normalized[0]);
        assertEquals('I', normalized[1]);
        assertEquals('F', normalized[2]);
        assertEquals('F', normalized[3]);

        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(
                new BufferedInputStream(new ByteArrayInputStream(normalized)))) {
            AudioFormat format = audioInputStream.getFormat();
            assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
            assertEquals(16_000f, format.getSampleRate());
            assertEquals(16, format.getSampleSizeInBits());
            assertEquals(1, format.getChannels());
            assertFalse(format.isBigEndian());
        }
    }

    @Test
    @DisplayName("normalizeWavToAliyunPreferredFormat should reject invalid wav payloads")
    void normalizeWavToAliyunPreferredFormat_ShouldRejectInvalidPayload() {
        SpeechTranscriptionException exception = assertThrows(
                SpeechTranscriptionException.class,
                () -> AliyunSpeechTranscriptionProvider.normalizeWavToAliyunPreferredFormat("not-a-real-wav".getBytes())
        );

        assertTrue(exception.getMessage().contains("录音文件"));
    }

    private byte[] buildSilentWav(AudioFormat format, int frameLength) throws Exception {
        byte[] pcm = new byte[frameLength * format.getFrameSize()];
        try (AudioInputStream inputStream = new AudioInputStream(
                new ByteArrayInputStream(pcm),
                format,
                frameLength
        ); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            AudioSystem.write(inputStream, javax.sound.sampled.AudioFileFormat.Type.WAVE, outputStream);
            return outputStream.toByteArray();
        }
    }
}
