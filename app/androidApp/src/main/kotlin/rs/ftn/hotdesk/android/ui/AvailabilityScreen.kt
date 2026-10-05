package rs.ftn.hotdesk.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import rs.ftn.hotdesk.shared.model.ResourceDto
import rs.ftn.hotdesk.shared.model.ResourceType
import rs.ftn.hotdesk.shared.rules.BookingRules

/**
 * Dnevna mreza slotova za jedan resurs: izbor termina i rezervacija.
 *
 * Ekran je "glup" - sve odluke (sta je dostupno, koliko se bira, da li izbor
 * prolazi pravila) donosi [AvailabilityViewModel] uz BookingRules iz :core.
 */
@Composable
fun AvailabilityScreen(resource: ResourceDto, onBack: () -> Unit) {
    // Jedan ViewModel po resursu; bez kljuca bi svi resursi delili isti.
    val vm: AvailabilityViewModel = viewModel(key = "availability-${resource.id}") {
        AvailabilityViewModel(resource)
    }
    LaunchedEffect(resource.id) { vm.onEnter() }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {

        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("< Nazad") }

        Text(resource.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            if (resource.type == ResourceType.MEETING_ROOM) {
                "${resource.location} · kapacitet ${resource.capacity}"
            } else {
                resource.location
            },
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = vm::previousDay, enabled = state.canGoBack) { Text("<") }
            Text(TimeFormat.day(state.dayAnchor), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = vm::nextDay) { Text(">") }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loadError != null -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.loadError!!, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = vm::load, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Pokusaj ponovo")
                    }
                }

                state.cells.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(state.cells) { i, cell ->
                        SlotCellView(cell, enabled = !state.isBooking) { vm.onSlotTap(i) }
                    }
                }
            }
        }

        Legend()

        val sel = state.selection
        Text(
            if (sel == null) {
                hint(resource.type)
            } else {
                val start = state.slots[sel.first].startTime
                val end = state.slots[sel.last].endTime
                "Izabrano: ${TimeFormat.hhmm(start)}-${TimeFormat.hhmm(end)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp)
        )

        state.notice?.let {
            Text(
                it.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (it.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Button(
            onClick = vm::book,
            enabled = sel != null && !state.isBooking,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            if (state.isBooking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(12.dp))
                Text("Rezervisanje...")
            } else {
                Text("Rezervisi")
            }
        }
    }
}

/** Uputstvo izvedeno iz BookingRules.defaultSlots - tekst prati pravilo, ne obrnuto. */
private fun hint(type: ResourceType): String {
    val n = BookingRules.defaultSlots(type)
    val trajanje = if (n >= BookingRules.slotsPerDay()) {
        "ceo preostali dan"
    } else {
        "${n * BookingRules.SLOT_MINUTES} min"
    }
    return "Dodir na slobodan termin bira $trajanje; dodir na kasniji termin pomera kraj."
}

@Composable
private fun SlotCellView(cell: SlotCell, enabled: Boolean, onClick: () -> Unit) {
    val (pozadina, tekst, okvir) = slotColors(cell.kind)
    Surface(
        onClick = onClick,
        enabled = enabled && (cell.kind == SlotKind.FREE || cell.kind == SlotKind.SELECTED),
        color = pozadina,
        contentColor = tekst,
        border = okvir,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(TimeFormat.hhmm(cell.slot.startTime), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun slotColors(kind: SlotKind): Triple<Color, Color, BorderStroke?> {
    val cs = MaterialTheme.colorScheme
    return when (kind) {
        SlotKind.FREE -> Triple(cs.surface, cs.onSurface, BorderStroke(1.dp, cs.outline))
        SlotKind.SELECTED -> Triple(cs.primary, cs.onPrimary, null)
        SlotKind.TAKEN -> Triple(cs.errorContainer, cs.onErrorContainer, null)
        SlotKind.BLOCKED -> Triple(cs.surfaceVariant, cs.onSurface.copy(alpha = 0.38f), null)
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(SlotKind.FREE, "slobodno")
        LegendItem(SlotKind.SELECTED, "izabrano")
        LegendItem(SlotKind.TAKEN, "zauzeto")
        LegendItem(SlotKind.BLOCKED, "proslo")
    }
}

@Composable
private fun LegendItem(kind: SlotKind, label: String) {
    val (pozadina, _, okvir) = slotColors(kind)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = pozadina, border = okvir, shape = CircleShape, modifier = Modifier.size(12.dp)) {}
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
