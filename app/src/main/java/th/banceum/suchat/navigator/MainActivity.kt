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
        Ads.start(this)
        setContent { NavigatorTheme { AppRoot() } }
    }
}
