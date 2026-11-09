package tiiehenry.viewcontroller

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import tiiehenry.viewcontroller.ui.GmNavHost
import tiiehenry.viewcontroller.ui.GmViewModel
import tiiehenry.viewcontroller.ui.theme.GodModeTheme

/**
 * 单 Activity 架构：全部界面由 Jetpack Compose 渲染，
 * Fragment / Preference 体系已整体移除。
 */
class SettingsActivity : AppCompatActivity() {

    private val viewModel: GmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GodModeTheme {
                GmNavHost(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startNotificationService()
    }

    private fun startNotificationService() {
        try {
            startService(Intent(this, NotificationService::class.java))
        } catch (e: IllegalStateException) {
            // Android 12+ 限制后台启动服务；本场景在 Activity 前台调用，正常不会触发
            e.printStackTrace()
        }
    }
}
