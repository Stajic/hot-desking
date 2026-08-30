package rs.ftn.hotdesk.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import rs.ftn.hotdesk.shared.model.ResourceDto
import rs.ftn.hotdesk.shared.model.ResourceType

/**
 * Faza 1 prikazuje samo listu resursa. To je dovoljno da se dokaze da podaci
 * putuju kroz sva tri modula: baza -> Ktor server -> :shared DTO -> Ktor client ->
 * ViewModel -> Compose.
 *
 * FAZA 2: ekran dostupnosti (mreza slotova), ekran "Moje rezervacije", prijava.
 */
@Composable
fun ResourceListScreen(vm: ResourceListViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {

        Text("Slobodni resursi", style = MaterialTheme.typography.headlineSmall)

        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.filter == null,
                onClick = { vm.setFilter(null) },
                label = { Text("Sve") }
            )
            FilterChip(
                selected = state.filter == ResourceType.DESK,
                onClick = { vm.setFilter(ResourceType.DESK) },
                label = { Text("Stolovi") }
            )
            FilterChip(
                selected = state.filter == ResourceType.MEETING_ROOM,
                onClick = { vm.setFilter(ResourceType.MEETING_ROOM) },
                label = { Text("Sale") }
            )
        }

        when {
            state.isLoading -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { CircularProgressIndicator() }

            state.error != null -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.error!!, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { vm.load() }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Pokusaj ponovo")
                }
            }

            state.resources.isEmpty() -> Text("Nema resursa koji odgovaraju filteru.")

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.resources, key = { it.id }) { ResourceCard(it) }
            }
        }
    }
}

@Composable
private fun ResourceCard(resource: ResourceDto) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(resource.name, style = MaterialTheme.typography.titleMedium)
            Text(
                resource.location,
                style = MaterialTheme.typography.bodyMedium
            )
            if (resource.type == ResourceType.MEETING_ROOM) {
                Text(
                    "Kapacitet: ${resource.capacity}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (resource.amenities.isNotEmpty()) {
                Text(
                    resource.amenities.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
