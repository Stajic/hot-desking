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
    data object MyBookings : Screen

    /** [saved]: naziv resursa koji je upravo sacuvan u formi, za poruku na ekranu. */
    data class AdminResources(val saved: String? = null) : Screen

    /** [resource] == null je nov resurs; [locations] su postojece lokacije za predloge. */
    data class ResourceForm(val resource: ResourceDto?, val locations: List<String>) : Screen

    /**
     * Globalni pregled rezervacija. [resourceId] je filter pri ulasku; [back] je ekran
     * sa kog je otvoren, jer se do pregleda stize i sa liste i iz upravljanja resursima.
     */
    data class AllBookings(val resourceId: String? = null, val back: Screen = Resources) : Screen
}

/**
 * Ekran na koji vodi "nazad": forma se vraca na upravljanje, globalni pregled tamo
 * odakle je otvoren, sve ostalo na listu.
 */
private fun Screen.parent(): Screen = when (this) {
    is Screen.ResourceForm -> Screen.AdminResources()
    is Screen.AllBookings -> back
    else -> Screen.Resources
}

/**
 * Navigacija za prijavljenog korisnika.
 *
 * ODLUKA: bez biblioteke za navigaciju. Ekrana je malo i dubina je najvise tri
 * nivoa (lista -> upravljanje resursima -> forma ili sve rezervacije), pa je stanje
 * jedna promenljiva,
 * a roditelja svakog ekrana daje [parent]. Sistemsko dugme "nazad" pokriva
 * BackHandler iz activity-compose, koji je vec medju zavisnostima. Biblioteka bi
 * donela novu zavisnost (i njene tranzitivne zavisnosti) bez stvarne dobiti.
 *
 * Stanje se pamti samo dok je ovaj composable prikazan: odjava ga uklanja, pa
 * sledeca prijava uvek krece od liste resursa.
 */
@Composable
fun AppNavigation() {
    var screen by remember { mutableStateOf<Screen>(Screen.Resources) }

    BackHandler(enabled = screen != Screen.Resources) { screen = screen.parent() }

    when (val s = screen) {
        Screen.Resources ->
            ResourceListScreen(
                onResourceClick = { screen = Screen.Availability(it) },
                onMyBookingsClick = { screen = Screen.MyBookings },
                onAdminClick = { screen = Screen.AdminResources() },
                onAllBookingsClick = { screen = Screen.AllBookings() }
            )

        is Screen.Availability ->
            AvailabilityScreen(resource = s.resource, onBack = { screen = Screen.Resources })

        Screen.MyBookings ->
            MyBookingsScreen(onBack = { screen = Screen.Resources })

        is Screen.AdminResources ->
            AdminResourcesScreen(
                saved = s.saved,
                onBack = { screen = Screen.Resources },
                onNew = { lokacije -> screen = Screen.ResourceForm(null, lokacije) },
                onEdit = { r, lokacije -> screen = Screen.ResourceForm(r, lokacije) },
                onShowBookings = { r ->
                    screen = Screen.AllBookings(resourceId = r.id, back = Screen.AdminResources())
                }
            )

        is Screen.AllBookings ->
            AllBookingsScreen(resourceId = s.resourceId, onBack = { screen = s.parent() })

        is Screen.ResourceForm ->
            ResourceFormScreen(
                resource = s.resource,
                locations = s.locations,
                onBack = { screen = s.parent() },
                onSaved = { naziv -> screen = Screen.AdminResources(saved = naziv) }
            )
    }
}
