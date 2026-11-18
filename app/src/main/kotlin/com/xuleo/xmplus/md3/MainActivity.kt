package com.xuleo.xmplus.md3

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.xuleo.xmplus.md3.core.designsystem.XmTheme
import com.xuleo.xmplus.md3.core.media.PlaybackOwner
import com.xuleo.xmplus.md3.core.media.PlaybackService
import com.xuleo.xmplus.md3.ui.XmApp
import java.util.concurrent.Future
import kotlinx.coroutines.flow.first

// AppCompatActivity: per-app language switching on API < 33 goes through AppCompat.
class MainActivity : AppCompatActivity() {
    private var mediaConnection: Future<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            // Asked when something first starts playing, not at launch, so the request has a reason the listener can see.
            LaunchedEffect(Unit) {
                val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (!allowed) {
                    (application as PlaybackOwner).playback.state.first { it.wantsToPlay }
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
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
