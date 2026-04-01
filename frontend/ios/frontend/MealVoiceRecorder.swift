import Foundation
import AVFoundation
import React

@objc(MealVoiceRecorder)
final class MealVoiceRecorder: NSObject {
  private var recorder: AVAudioRecorder?
  private var recordingURL: URL?
  private var startedAt: Date?

  @objc
  static func requiresMainQueueSetup() -> Bool {
    true
  }

  @objc(requestPermission:rejecter:)
  func requestPermission(_ resolve: @escaping RCTPromiseResolveBlock,
                         rejecter reject: @escaping RCTPromiseRejectBlock) {
    AVAudioSession.sharedInstance().requestRecordPermission { granted in
      resolve(granted)
    }
  }

  @objc(startRecording:rejecter:)
  func startRecording(_ resolve: @escaping RCTPromiseResolveBlock,
                      rejecter reject: @escaping RCTPromiseRejectBlock) {
    let session = AVAudioSession.sharedInstance()

    do {
      try session.setCategory(.playAndRecord, mode: .measurement, options: [.defaultToSpeaker, .allowBluetoothHFP])
      try session.setActive(true, options: [])
      // These are hardware hints only; ignore errors on devices that don't support the exact values.
      try? session.setPreferredSampleRate(16_000)
      try? session.setPreferredInputNumberOfChannels(1)

      let url = buildRecordingURL()
      let settings: [String: Any] = [
        // Use 16 kHz mono PCM WAV so the uploaded file matches Aliyun ASR
        // sample-rate expectations instead of relying on AAC encoder behavior.
        AVFormatIDKey: Int(kAudioFormatLinearPCM),
        AVSampleRateKey: 16_000,
        AVNumberOfChannelsKey: 1,
        AVLinearPCMBitDepthKey: 16,
        AVLinearPCMIsBigEndianKey: false,
        AVLinearPCMIsFloatKey: false,
      ]

      recorder = try AVAudioRecorder(url: url, settings: settings)
      recorder?.prepareToRecord()

      guard recorder?.record() == true else {
        reject("meal_voice_recorder_start_failed", "Unable to start recording", nil)
        return
      }

      recordingURL = url
      startedAt = Date()
      resolve([
        "uri": url.absoluteString,
        "path": url.path,
      ])
    } catch {
      reject("meal_voice_recorder_start_failed", error.localizedDescription, error)
    }
  }

  @objc(stopRecording:rejecter:)
  func stopRecording(_ resolve: @escaping RCTPromiseResolveBlock,
                     rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard let recorder = recorder, let recordingURL = recordingURL else {
      reject("meal_voice_recorder_not_recording", "No active recording session", nil)
      return
    }

    recorder.stop()
    self.recorder = nil

    let durationMs = max(0, Int(((Date().timeIntervalSince(startedAt ?? Date())) * 1000).rounded()))
    startedAt = nil
    self.recordingURL = nil

    resolve([
      "uri": recordingURL.absoluteString,
      "path": recordingURL.path,
      "durationMs": durationMs,
    ])
  }

  private func buildRecordingURL() -> URL {
    let tempDir = FileManager.default.temporaryDirectory
    let fileName = "what-to-eat-\(UUID().uuidString).wav"
    return tempDir.appendingPathComponent(fileName)
  }
}
