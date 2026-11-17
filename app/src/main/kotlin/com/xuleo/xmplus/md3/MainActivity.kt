package com.xuleo.xmplus.md3

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.xuleo.xmplus.md3.core.designsystem.XmTheme
import com.xuleo.xmplus.md3.ui.XmApp

// AppCompatActivity: per-app language switching on API < 33 goes through AppCompat.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XmTheme {
                XmApp()
            }
        }
    }
}