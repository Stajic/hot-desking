package rs.ftn.hotdesk.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import rs.ftn.hotdesk.client.TimeFormat
import rs.ftn.hotdesk.shared.model.SlotDto
import rs.ftn.hotdesk.shared.rules.BookingRules
import rs.ftn.hotdesk.shared.rules.RoomStatus
import rs.ftn.hotdesk.shared.rules.TimeRange

private val SLOBODNO = Color(0xFF2E7D32)
private val ZAUZETO = Color(0xFFC62828)
private val VAN = Color(0xFF455A64)

/**
 * Ekran pored sale: da li je slobodna, do kada, sta je sledece i danasnji raspored.
 *
 * Samo prikaz. Status racuna RoomStatus iz modula :core, iz poslednje mreze i
 * trenutnog vremena; vreme formatira TimeFormat iz :app:shared - isto kao na telefonu.
 * Jedini taster koji ekran koristi je OK, za promenu sale.
 */
@Composable
fun RoomInfoScreen(room: Room, onChangeRoom: () -> Unit) {
    // Jedan ViewModel po sali: promena sale pokrece osvezavanje za novu.
    val vm: RoomInfoViewModel = viewModel(key = "sala-${room.id}") { RoomInfoViewModel(room.id) }
    val state by vm.state.collectAsStateWithLifecycle()

    // Sat ekrana; status se iz njega preracunava svake sekunde.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    // Ekran mora da ima fokus da bi primio taster OK sa daljinskog upravljaca.
    val fokus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fokus.requestFocus() }

    val mreza = state.availability
    val status = mreza?.let { RoomStatus.of(it, now) }

    Column(
        Modifier
            .fillMaxSize()
            // onKeyEvent mora biti iznad (levo od) elementa koji ima fokus, da bi taster
            // stigao do njega.
            .onKeyEvent { e ->
                // Na otpustanje tastera: pritisak tada ne stize do ekrana za izbor sale.
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok && e.type == KeyEventType.KeyUp) {
                    onChangeRoom()
                    true
                } else {
                    false
                }
            }
            .focusRequester(fokus)
            .focusable()
            // Sistemske trake (na televizoru ih obicno nema, na telefonu ima).
            .safeDrawingPadding()
            // Margine od 5 %: televizori cesto seku ivice slike.
            .padding(horizontal = 48.dp, vertical = 27.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(room.name, style = MaterialTheme.typography.displayMedium)
                Text(room.location, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(TimeFormat.hhmm(now), style = MaterialTheme.typography.displayMedium)
                Text(TimeFormat.day(now), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Status dobija sav prostor izmedju zaglavlja i trake, a velicina slova se racuna
        // iz tog prostora: raspored tako staje na svaki ekran, i na televizor (960x540 dp)
        // i na telefon polozen na bok (oko 400 dp visine).
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (status == null) {
                Text(
                    if (state.offline) "Nema veze sa serverom na ${Server.baseUrl}." else "Ucitavanje...",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                // Naslov, dva reda od 0,45 naslova i unutrasnja margina zajedno su oko
                // 2,9 visine naslova; deljenje sa 3 ostavlja malu rezervu.
                StatusBlock(status, naslov = minOf(maxHeight / 3f, 80.dp))
            }
        }

        if (mreza != null) {
            Timeline(mreza.slots, now)
            Spacer(Modifier.height(16.dp))
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val podaci = state.lastOk?.let { "podaci iz ${TimeFormat.hhmm(it)}" } ?: "nema podataka"
            Text(
                if (state.offline) "Nema veze sa serverom; $podaci." else "Osvezeno u ${state.lastOk?.let(TimeFormat::hhmm) ?: "-"}.",
                style = MaterialTheme.typography.titleMedium,
                color = if (state.offline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text("OK: promena sale", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** @param naslov visina slova naslova; redovi i margine se racunaju iz nje. */
@Composable
private fun StatusBlock(status: RoomStatus, naslov: Dp) {
    val (tekst, boja, redovi) = when (status) {
        is RoomStatus.Free -> Triple(
            "SLOBODNO",
            SLOBODNO,
            listOfNotNull(
                if (status.next != null) "do ${TimeFormat.hhmm(status.until)}" else "do kraja radnog dana",
                status.next?.let(::sledece)
            )
        )
        is RoomStatus.Occupied -> Triple(
            "ZAUZETO",
            ZAUZETO,
            listOfNotNull("do ${TimeFormat.hhmm(status.until)}", status.next?.let(::sledece))
        )
        RoomStatus.OutsideHours -> Triple(
            "VAN RADNOG VREMENA",
            VAN,
            listOf("Radno vreme je od ${BookingRules.DAY_START_HOUR}:00 do ${BookingRules.DAY_END_HOUR}:00.")
        )
    }

    // Pretvaranje preko gustine uzima u obzir i velicinu slova iz podesavanja uredjaja.
    val (naslovSp, redSp) = with(LocalDensity.current) { naslov.toSp() to (naslov * 0.45f).toSp() }

    Column(
        Modifier
            .fillMaxWidth()
            .background(boja, RoundedCornerShape(24.dp))
            .padding(horizontal = naslov / 2, vertical = naslov * 0.3f)
    ) {
        // Visina reda je zadata izricito, da bi racun velicine iznad vazio.
        Text(tekst, fontSize = naslovSp, lineHeight = 1.2.em, fontWeight = FontWeight.Bold, color = Color.White)
        redovi.forEach { Text(it, fontSize = redSp, lineHeight = 1.2.em, color = Color.White) }
    }
}

private fun sledece(r: TimeRange) = "Sledece zauzece: ${TimeFormat.hhmm(r.start)}-${TimeFormat.hhmm(r.end)}"

/** Danasnjih 24 slota: zauzeti crveno, slobodni zeleno, prosli bledo, tekuci uokviren. */
@Composable
private fun Timeline(slots: List<SlotDto>, now: Long) {
    if (slots.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        slots.forEach { s ->
            val proslo = s.endTime <= now
            val tekuci = now >= s.startTime && now < s.endTime
            val boja = (if (s.isAvailable) SLOBODNO else ZAUZETO).copy(alpha = if (proslo) 0.3f else 1f)
            val oblik = RoundedCornerShape(4.dp)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(boja, oblik)
                    .then(if (tekuci) Modifier.border(3.dp, Color.White, oblik) else Modifier)
            )
        }
    }
    // Oznake na svaka dva sata, ispod pocetka odgovarajuceg slota.
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        slots.chunked(4).forEach { grupa ->
            Text(
                TimeFormat.hhmm(grupa.first().startTime),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(grupa.size.toFloat())
            )
        }
    }
}
