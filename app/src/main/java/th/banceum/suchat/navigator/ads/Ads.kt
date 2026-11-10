package th.banceum.suchat.navigator.ads

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import th.banceum.suchat.navigator.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * โฆษณา AdMob: แบนเนอร์ด้านล่างจอ (ซ่อนระหว่างนำทาง) + โฆษณาเต็มจอหลังจบการนำทาง (เว้นอย่างน้อย 3 นาที)
 * ไม่แสดงโฆษณาใด ๆ ระหว่างนำทาง เพื่อไม่รบกวนผู้ขับขี่
 * ขอความยินยอมผ่าน UMP ก่อนโหลดโฆษณา (จำเป็นสำหรับผู้ใช้ในยุโรป/สหราชอาณาจักร)
 */
object Ads {
    var ready by mutableStateOf(false)
        private set
    private val started = AtomicBoolean(false)
    private var lastInterstitial = 0L

    fun start(activity: Activity) {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity, ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (consent.canRequestAds()) init(activity)
                }
            },
            { if (consent.canRequestAds()) init(activity) },
        )
        if (consent.canRequestAds()) init(activity)
    }

    private fun init(activity: Activity) {
        if (!started.compareAndSet(false, true)) return
        // ผู้ใช้อายุ 18 ปีขึ้นไป แต่เป็นแอปใช้ทั่วไป จำกัดเนื้อหาโฆษณาไม่เกินระดับ PG
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG).build(),
        )
        MobileAds.initialize(activity.applicationContext) { ready = true }
    }

    /** เรียกหลังผู้ใช้กด "จบการนำทาง" หรือถึงจุดหมายแล้วปิดหน้านำทาง */
    fun maybeShowInterstitial(activity: Activity) {
        val now = System.currentTimeMillis()
        if (!ready || BuildConfig.ADMOB_INTERSTITIAL_ID.isBlank() || now - lastInterstitial < 3 * 60_000) return
        lastInterstitial = now
        InterstitialAd.load(activity, BuildConfig.ADMOB_INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) = ad.show(activity)
                override fun onAdFailedToLoad(error: LoadAdError) {}
            })
    }
}

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    if (!Ads.ready || BuildConfig.ADMOB_BANNER_ID.isBlank()) return
    val widthDp = LocalConfiguration.current.screenWidthDp
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                adUnitId = BuildConfig.ADMOB_BANNER_ID
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() },
    )
}
