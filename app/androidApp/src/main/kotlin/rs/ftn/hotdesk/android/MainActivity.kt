package rs.ftn.hotdesk.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.ftn.hotdesk.android.data.ApiClient
import rs.ftn.hotdesk.android.data.Session
import rs.ftn.hotdesk.android.ui.LoginScreen
import rs.ftn.hotdesk.android.ui.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Emulator: 10.0.2.2 je host masina.
        // Fizicki telefon: zameni IP adresom laptopa (Windows: `ipconfig`).
        ApiClient.baseUrl = "http://192.168.1.5:8080"

        setContent {
            MaterialTheme {
                Surface {
                    // Jedini uslov za prelazak sa prijave na listu je postojanje
                    // sesije; dalje kretanje kroz ekrane vodi AppNavigation.
                    val korisnik by Session.user.collectAsStateWithLifecycle()
                    if (korisnik == null) LoginScreen() else AppNavigation()
                }
            }
        }
    }
}
