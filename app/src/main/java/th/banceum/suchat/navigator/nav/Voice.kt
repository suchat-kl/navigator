package th.banceum.suchat.navigator.nav

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** เสียงพูดนำทางภาษาไทยด้วย TextToSpeech ของเครื่อง (ไม่มีเสียงไทยในเครื่อง = เงียบ แจ้งผ่าน [available]) */
class Voice(context: Context) {
    private var ready = false
    var available: Boolean? = null
        private set
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        available = ready && setThai()
    }

    private fun setThai(): Boolean {
        val r = tts.setLanguage(Locale.forLanguageTag("th-TH"))
        return r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun say(text: String) {
        if (ready && available == true) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav")
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun shutdown() = tts.shutdown()
}
