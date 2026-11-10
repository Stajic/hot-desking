package rs.ftn.hotdesk.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import rs.ftn.hotdesk.shared.model.ResourceDto
import rs.ftn.hotdesk.shared.model.ResourceType

/**
 * Upravljanje resursima (samo ADMIN): svi resursi, i neaktivni, sa deaktivacijom i
 * aktivacijom. Ulaz je na listi resursa i vidljiv je samo administratoru; server
 * ionako svakom drugom vraca 403.
 *
 * Dodir na karticu otvara izmenu resursa, a "Novi resurs" praznu formu
 * ([ResourceFormScreen]). Forma dobija i postojece lokacije, da ih ponudi kao cipove.
 *
 * @param saved naziv resursa koji je upravo sacuvan u formi, za poruku na vrhu.
 */
@Composable
fun AdminResourcesScreen(
    onBack: () -> Unit,
    onNew: (locations: List<String>) -> Unit,
    onEdit: (resource: ResourceDto, locations: List<String>) -> Unit,
    onShowBookings: (resource: ResourceDto) -> Unit,
    saved: String? = null,
    vm: AdminResourcesViewModel = viewModel()
) {
    LaunchedEffect(saved) { vm.onEnter(saved) }
    val state by vm.state.collectAsStateWithLifecycle()

    val lokacije = state.resources.map { it.location }.distinct().sorted()

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {

        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("< Nazad") }
        Text("Upravljanje resursima", style = MaterialTheme.typography.headlineSmall)

        OutlinedButton(onClick = { onNew(lokacije) }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Novi resurs")
        }

        state.notice?.let {
            Text(
                it.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (it.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
            when {
                state.error != null -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error!!, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = vm::load, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Pokusaj ponovo")
                    }
                }

                state.isLoading && state.resources.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.resources, key = { it.id }) { r ->
                        AdminResourceCard(
                            resource = r,
                            onClick = { onEdit(r, lokacije) },
                            trailing = {
                                when {
                                    state.changingId == r.id ->
                                        CircularProgressIndicator(Modifier.padding(8.dp), strokeWidth = 2.dp)

                                    r.isActive ->
                                        TextButton(
                                            onClick = { vm.askDeactivate(r) },
                                            enabled = state.changingId == null
                                        ) { Text("Deaktiviraj") }

                                    else ->
                                        TextButton(
                                            onClick = { vm.activate(r) },
                                            enabled = state.changingId == null
                                        ) { Text("Aktiviraj") }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    state.confirming?.let { r ->
        val broj = state.activeBookings
        AlertDialog(
            onDismissRequest = vm::dismissDeactivate,
            // Naziv ide u recenicu, ne u naslov: "Deaktivirati Sala Sava?" bi trazilo
            // akuzativ, a naziv resursa se ne moze automatski menjati po padezima.
            title = { Text("Deaktivirati resurs?") },
            text = {
                Column {
                    Text(
                        "${r.name} izlazi iz ponude za nove rezervacije.\n\n" + when {
                            state.countFailed -> "Broj postojecih rezervacija nije moguce utvrditi."
                            broj == null -> "Proveravam postojece rezervacije..."
                            else -> remainingBookings(broj)
                        }
                    )
                    // Odluka je da deaktivacija ne otkazuje rezervacije; ovde administrator
                    // jednim dodirom vidi koje su to i moze da ih otkaze pojedinacno.
                    if (broj != null && broj > 0) {
                        TextButton(
                            onClick = {
                                vm.dismissDeactivate()
                                onShowBookings(r)
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("Prikazi rezervacije") }
                    }
                }
            },
            // Potvrda tek kad je posledica poznata (ili kad se zna da se ne moze utvrditi).
            confirmButton = {
                TextButton(
                    onClick = vm::confirmDeactivate,
                    enabled = broj != null || state.countFailed
                ) { Text("Deaktiviraj") }
            },
            dismissButton = { TextButton(onClick = vm::dismissDeactivate) { Text("Odustani") } }
        )
    }
}

/** Recenica o rezervacijama koje ostaju nad resursom, sa ispravnim padezom za broj. */
private fun remainingBookings(n: Int): String {
    if (n == 0) return "Nad resursom nema aktivnih rezervacija."
    val jedinice = n % 10
    val desetice = n % 100
    val koliko = when {
        jedinice == 1 && desetice != 11 -> "ostaje $n aktivna rezervacija"
        jedinice in 2..4 && desetice !in 12..14 -> "ostaju $n aktivne rezervacije"
        else -> "ostaje $n aktivnih rezervacija"
    }
    return "Nad resursom $koliko. Postojece rezervacije se ne otkazuju automatski."
}

@Composable
private fun AdminResourceCard(resource: ResourceDto, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    // Neaktivan resurs je bled, isto kao istorija u Mojim rezervacijama.
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (!resource.isActive) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(resource.name, style = MaterialTheme.typography.titleMedium)
                Text(resource.location, style = MaterialTheme.typography.bodyMedium)
                Text(
                    buildString {
                        append(
                            if (resource.type == ResourceType.DESK) "Sto"
                            else "Sala, kapacitet ${resource.capacity}"
                        )
                        if (!resource.isActive) append(" · neaktivan")
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            trailing()
        }
    }
}
