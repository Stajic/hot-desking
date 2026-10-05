package rs.ftn.hotdesk.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import rs.ftn.hotdesk.shared.model.ResourceDto

/** Ekrani dostupni prijavljenom korisniku. */
sealed interface Screen {
    data object Resources : Screen
    data class Availability(val resource: ResourceDto) : Screen
}

/**
 * Navigacija za prijavljenog korisnika.
 *
 * ODLUKA: bez biblioteke za navigaciju. Ekrana je malo i dubina je najvise dva
 * nivoa, pa je stanje jedna promenljiva, a sistemsko dugme "nazad" pokriva
 * BackHandler iz activity-compose, koji je vec medju zavisnostima. Biblioteka bi
 * donela novu zavisnost (i njene tranzitivne zavisnosti) bez stvarne dobiti.
 *
 * Stanje se pamti samo dok je ovaj composable prikazan: odjava ga uklanja, pa
 * sledeca prijava uvek krece od liste resursa.
 */
@Composable
fun AppNavigation() {
    var screen by remember { mutableStateOf<Screen>(Screen.Resources) }

    BackHandler(enabled = screen != Screen.Resources) { screen = Screen.Resources }

    when (val s = screen) {
        Screen.Resources ->
            ResourceListScreen(onResourceClick = { screen = Screen.Availability(it) })

        is Screen.Availability ->
            AvailabilityScreen(resource = s.resource, onBack = { screen = Screen.Resources })
    }
}
