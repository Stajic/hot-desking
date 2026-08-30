package rs.ftn.hotdesk.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import rs.ftn.hotdesk.android.data.ApiClient
import rs.ftn.hotdesk.android.ui.ResourceListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Emulator: 10.0.2.2 je host masina.
        // Fizicki telefon: zameni IP adresom laptopa (Windows: `ipconfig`).
        ApiClient.baseUrl = "http://192.168.1.5:8080"

        // TODO(auth): zameniti prijavom. Za sada se ID uzima iz odgovora
        // GET /api/resources nije potreban, pa se prijava zaobilazi u fazi 1.
        ApiClient.currentUserId = null

        setContent {
            MaterialTheme {
                Surface { ResourceListScreen() }
            }
        }
    }
}
