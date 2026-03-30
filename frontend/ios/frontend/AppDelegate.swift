import UIKit
import React
import React_RCTAppDelegate
import ReactAppDependencyProvider
#if canImport(HeroWechat)
import HeroWechat
#endif

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
  var window: UIWindow?

  var reactNativeDelegate: ReactNativeDelegate?
  var reactNativeFactory: RCTReactNativeFactory?

  func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    let delegate = ReactNativeDelegate()
    let factory = RCTReactNativeFactory(delegate: delegate)
    delegate.dependencyProvider = RCTAppDependencyProvider()

    reactNativeDelegate = delegate
    reactNativeFactory = factory

    window = UIWindow(frame: UIScreen.main.bounds)

    factory.startReactNative(
      withModuleName: "frontend",
      in: window,
      launchOptions: launchOptions
    )

    return true
  }

  func application(
    _ application: UIApplication,
    open url: URL,
    options: [UIApplication.OpenURLOptionsKey: Any] = [:]
  ) -> Bool {
#if canImport(HeroWechat)
    // HeroWechat owns WeChat callback routing; AppDelegate only forwards the
    // URL so the bridge module can emit the correct JS-side event.
    return RNTWechat.handleOpenURL(application, openURL: url, options: options)
#else
    return false
#endif
  }

  func application(
    _ application: UIApplication,
    continue userActivity: NSUserActivity,
    restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void
  ) -> Bool {
#if canImport(HeroWechat)
    return RNTWechat.handleOpenUniversalLink(userActivity)
#else
    return false
#endif
  }
}

class ReactNativeDelegate: RCTDefaultReactNativeFactoryDelegate {
  private func embeddedBundleURL() -> URL? {
    Bundle.main.url(forResource: "main", withExtension: "jsbundle")
  }

  private func localMetroBundleURL() -> URL? {
    var components = URLComponents()
    components.scheme = "http"
    components.host = "127.0.0.1"
    components.port = 8081
    components.path = "/index.bundle"
    components.queryItems = [
      URLQueryItem(name: "platform", value: "ios"),
      URLQueryItem(name: "dev", value: "true"),
      URLQueryItem(name: "lazy", value: "true"),
      URLQueryItem(name: "minify", value: "false"),
      URLQueryItem(name: "inlineSourceMap", value: "false"),
      URLQueryItem(name: "modulesOnly", value: "false"),
      URLQueryItem(name: "runModule", value: "true"),
      URLQueryItem(name: "app", value: Bundle.main.bundleIdentifier),
    ]
    return components.url
  }

  override func sourceURL(for bridge: RCTBridge) -> URL? {
    self.bundleURL()
  }

  override func bundleURL() -> URL? {
#if DEBUG
    #if targetEnvironment(simulator)
      return RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
        ?? localMetroBundleURL()
    #else
      // Physical-device validation should not depend on Metro being reachable.
      return embeddedBundleURL()
        ?? RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
    #endif
#else
    return embeddedBundleURL()
#endif
  }
}
