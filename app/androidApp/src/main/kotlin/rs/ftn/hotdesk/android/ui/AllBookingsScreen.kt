package rs.ftn.hotdesk.android.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import rs.ftn.hotdesk.shared.model.BookingDto
import rs.ftn.hotdesk.shared.model.BookingStatus

/**
 * Sve rezervacije (samo ADMIN): aktivne i istorija svih korisnika, sa filterima po
 * korisniku i resursu i otkazivanjem bilo koje aktivne rezervacije.
 *
 * @param resourceId filter po resursu pri ulasku - pregled otvoren iz dijaloga
 *        deaktivacije prikazuje odmah rezervacije nad tim resursom.
 */
@Composable
fun AllBookingsScreen(
    onBack: () -> Unit,
    resourceId: String? = null,
    vm: AllBookingsViewModel = viewModel()
) {
    LaunchedEffect(resourceId) { vm.onEnter(resourceId) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {

        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("< Nazad") }
        Text("Sve rezervacije", style = MaterialTheme.typography.headlineSmall)

        // Ponovni dodir na izabrani cip ponistava izbor, kao kod lokacije na listi resursa.
        FilterRow(
            allLabel = "Svi korisnici",
            options = state.userOptions,
            selected = state.userId,
            onSelect = vm::setUser
        )
        FilterRow(
            allLabel = "Svi resursi",
            options = state.resourceOptions,
            selected = state.resourceId,
            onSelect = vm::setResource
        )

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

                state.isLoading && state.active.isEmpty() && state.history.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.active.isEmpty() && state.history.isEmpty() ->
                    Text(
                        if (state.hasFilters) "Nema rezervacija za izabrani filter." else "Nema rezervacija.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SectionTitle("Aktivne (${state.active.size})") }
                    if (state.active.isEmpty()) {
                        item { Text("Nema aktivnih rezervacija.", style = MaterialTheme.typography.bodyMedium) }
                    }
                    items(state.active, key = { it.id }) { b ->
                        AdminBookingCard(
                            booking = b,
                            muted = false,
                            trailing = {
                                when {
                                    state.cancellingId == b.id ->
                                        CircularProgressIndicator(Modifier.padding(8.dp), strokeWidth = 2.dp)

                                    vm.canCancel(b) ->
                                        TextButton(
                                            onClick = { vm.askCancel(b) },
                                            enabled = state.cancellingId == null
                                        ) { Text("Otkazi") }

                                    else ->
                                        Text("u toku", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        )
                    }

                    item { SectionTitle("Istorija (${state.history.size})", topPadding = 16) }
                    items(state.history, key = { it.id }) { b ->
                        AdminBookingCard(
                            booking = b,
                            muted = true,
                            trailing = {
                                Text(
                                    if (b.status == BookingStatus.CANCELLED) "otkazana" else "zavrsena",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    state.confirming?.let { b ->
        AlertDialog(
            onDismissRequest = vm::dismissCancel,
            title = { Text("Otkazati rezervaciju?") },
            text = {
                Text(
                    "${b.resourceName}\n${b.userName}\n${TimeFormat.day(b.startTime)}\n" +
                        "${TimeFormat.hhmm(b.startTime)}-${TimeFormat.hhmm(b.endTime)}\n\n" +
                        // Sistem obavestenja ne postoji - isti razlog zbog kog deaktivacija
                        // resursa ne otkazuje rezervacije automatski.
                        "Termin ce odmah postati slobodan. Korisnik nece dobiti obavestenje."
                )
            },
            confirmButton = { TextButton(onClick = vm::confirmCancel) { Text("Otkazi rezervaciju") } },
            dismissButton = { TextButton(onClick = vm::dismissCancel) { Text("Odustani") } }
        )
    }
}

/** Red cipova sa jednim izborom: "svi" ili jedna vrednost; ponovni dodir ponistava izbor. */
@Composable
private fun FilterRow(
    allLabel: String,
    options: List<FilterOption>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    if (options.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(allLabel) })
        options.forEach { o ->
            FilterChip(
                selected = selected == o.id,
                onClick = { onSelect(if (selected == o.id) null else o.id) },
                label = { Text(o.label) }
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, topPadding: Int = 0) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = topPadding.dp, bottom = 4.dp)
    )
}

/** Kao kartica u Mojim rezervacijama, uz ime korisnika. */
@Composable
private fun AdminBookingCard(booking: BookingDto, muted: Boolean, trailing: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (muted) {
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
                Text(booking.resourceName, style = MaterialTheme.typography.titleMedium)
                Text(booking.userName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${TimeFormat.day(booking.startTime)}, " +
                        "${TimeFormat.hhmm(booking.startTime)}-${TimeFormat.hhmm(booking.endTime)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            trailing()
        }
    }
}
