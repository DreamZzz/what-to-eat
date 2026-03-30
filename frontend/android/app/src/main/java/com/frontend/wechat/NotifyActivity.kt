package com.frontend.wechat

import android.app.Activity
import android.os.Bundle
import com.github.reactnativehero.wechat.RNTWechatModule

class NotifyActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    RNTWechatModule.handleIntent(intent)
    finish()
  }
}
