package rs.ftn.hotdesk.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import rs.ftn.hotdesk.shared.model.ResourceDto

/**
 * Izbor sale koju ekran prikazuje. Jedini ulaz je daljinski upravljac: strelice
 * pomeraju fokus, OK bira.
 *
 * @param current sala koja je vec izabrana; ona dobija pocetni fokus.
 * @param onBack povratak na ekran sale bez promene; null pri prvom pokretanju, kad
 *        izabrane sale jos nema.
 */
@Composable
fun RoomPickerScreen(
    current: String?,
    onPick: (Room) -> Unit,
    onBack: (() -> Unit)?,
    vm: RoomPickerViewModel = viewModel()
) {
    onBack?.let { BackHandler(onBack = it) }
    LaunchedEffect(Unit) { vm.load() }
    val state by vm.state.collectAsStateWithLifecycle()

    // Prvo sistemske trake (na televizoru ih obicno nema), pa margine od 5 %:
    // televizori cesto seku ivice slike.
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 48.dp, vertical = 27.dp)) {
        Text("Izaberite salu", style = MaterialTheme.typography.displaySmall)
        Text(
            "Strelice gore i dole, OK za izbor. Izbor se pamti na uredjaju.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        when {
            state.error != null -> {
                val ponovo = remember { FocusRequester() }
                Text(state.error!!, style = MaterialTheme.typography.titleLarge)
                Button(onClick = vm::load, modifier = Modifier.padding(top = 16.dp).focusRequester(ponovo)) {
                    Text("Pokusaj ponovo")
                }
                LaunchedEffect(state.error) { ponovo.requestFocus() }
            }

            state.isLoading -> CircularProgressIndicator()

            state.rooms.isEmpty() -> Text("Nema aktivnih sala.", style = MaterialTheme.typography.titleLarge)

            else -> {
                // Pocetni fokus: vec izabrana sala, inace prva. Sala ima malo, pa je
                // obicna kolona dovoljna i svaka stavka je sigurno na ekranu kad dobije fokus.
                val pocetna = state.rooms.indexOfFirst { it.id == current }.coerceAtLeast(0)
                val fokus = remember { FocusRequester() }
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.rooms.forEachIndexed { i, r ->
                        RoomItem(
                            room = r,
                            isCurrent = r.id == current,
                            modifier = if (i == pocetna) Modifier.focusRequester(fokus) else Modifier,
                            onClick = { onPick(Room(r.id, r.name, r.location)) }
                        )
                    }
                }
                LaunchedEffect(state.rooms) { fokus.requestFocus() }
            }
        }
    }
}

@Composable
private fun RoomItem(room: ResourceDto, isCurrent: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // Na televizoru nema dodira: korisnik mora jasno da vidi gde je fokus.
    var fokusirano by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { fokusirano = it.hasFocus },
        shape = RoundedCornerShape(12.dp),
        color = if (fokusirano) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (fokusirano) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(room.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${room.location} · kapacitet ${room.capacity}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (isCurrent) Text("trenutno", style = MaterialTheme.typography.titleMedium)
        }
    }
}
