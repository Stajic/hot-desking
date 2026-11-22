package rs.ftn.hotdesk.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Ekran sa informacijama o zauzetosti sale, za STB uredjaj sa Android TV-om.
 *
 * Pri prvom pokretanju bira se sala; izbor se pamti ([RoomStore]), pa se posle
 * restarta odmah prikazuje ta sala. Taster OK na ekranu sale vraca na izbor.
 */
class TvActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ekran na zidu radi bez nadzora i ne sme da se ugasi.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Emulator: 10.0.2.2 je host masina. Uredjaj na istoj mrezi: IP laptopa,
        // isto kao u aplikaciji na telefonu (i u network_security_config.xml).
        Server.baseUrl = "http://192.168.1.5:8080"

        val store = RoomStore(this)

        setContent {
            // Tamna tema: ekran stalno svetli, a tamna pozadina manje zaslepljuje.
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    var sala by remember { mutableStateOf(store.load()) }
                    var biranje by remember { mutableStateOf(sala == null) }

                    val trenutna = sala
                    if (biranje || trenutna == null) {
                        RoomPickerScreen(
                            current = trenutna?.id,
                            onPick = { izabrana ->
                                store.save(izabrana)
                                sala = izabrana
                                biranje = false
                            },
                            onBack = if (trenutna != null) ({ biranje = false }) else null
                        )
                    } else {
                        RoomInfoScreen(room = trenutna, onChangeRoom = { biranje = true })
                    }
                }
            }
        }
    }
}
