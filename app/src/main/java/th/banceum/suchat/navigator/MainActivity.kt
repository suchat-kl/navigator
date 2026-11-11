package th.banceum.suchat.navigator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import th.banceum.suchat.navigator.ads.Ads
import th.banceum.suchat.navigator.ui.AppRoot
import th.banceum.suchat.navigator.ui.NavigatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // ถ่ายภาพหน้าจอสำหรับหน้าร้าน Play โดยไม่มีแบนเนอร์ "Test Ad" (เฉพาะรุ่น debug)
        // adb shell am start -n th.banceum.suchat.navigator/.MainActivity --ez screenshots true
        val screenshots = BuildConfig.DEBUG && intent.getBooleanExtra("screenshots", false)
        if (!screenshots) Ads.start(this)
        setContent { NavigatorTheme { AppRoot() } }
    }
}
