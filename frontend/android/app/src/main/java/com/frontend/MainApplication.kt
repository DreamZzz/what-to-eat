package com.frontend

import android.app.Application
import com.github.reactnativehero.wechat.RNTWechatModule
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost

class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          // Packages that cannot be autolinked yet can be added manually here, for example:
          // add(MyReactNativePackage())
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    // Current post sharing uses text payloads only, so a no-op image loader keeps
    // the native module initialized without adding an extra image pipeline.
    RNTWechatModule.init { _, onComplete ->
      onComplete(null)
    }
    loadReactNative(this)
  }
}
