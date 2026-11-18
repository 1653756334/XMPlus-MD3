package com.xuleo.xmplus.md3

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.xuleo.xmplus.md3.core.designsystem.XmTheme
import com.xuleo.xmplus.md3.core.media.PlaybackService
import com.xuleo.xmplus.md3.ui.XmApp
import java.util.concurrent.Future

// AppCompatActivity: per-app language switching on API < 33 goes through AppCompat.
class MainActivity : AppCompatActivity() {
    private var mediaConnection: Future<MediaController>? = null

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

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        mediaConnection = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        mediaConnection?.let(MediaController::releaseFuture)
        mediaConnection = null
        super.onStop()
    }
}
