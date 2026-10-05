package rs.ftn.hotdesk.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.ftn.hotdesk.android.data.ApiClient
import rs.ftn.hotdesk.shared.model.CreateBookingRequest
import rs.ftn.hotdesk.shared.model.ResourceDto
import rs.ftn.hotdesk.shared.model.SlotDto
import rs.ftn.hotdesk.shared.rules.BookingRules

enum class SlotKind {
    FREE,
    SELECTED,
    TAKEN,

    /** Server kaze da je slobodan, ali BookingRules.validate ga odbija - u praksi prosao termin. */
    BLOCKED
}

data class SlotCell(val slot: SlotDto, val kind: SlotKind)

data class Notice(val text: String, val isError: Boolean)

data class AvailabilityState(
    val dayAnchor: Long,
    val canGoBack: Boolean = false,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val slots: List<SlotDto> = emptyList(),
    val cells: List<SlotCell> = emptyList(),
    val selection: IntRange? = null,
    val isBooking: Boolean = false,
    val notice: Notice? = null
)

/**
 * Dnevna mreza slotova za jedan resurs i rezervacija.
 *
 * Ovde se BookingRules iz modula :core izvrsava NA KLIJENTU - ista funkcija koju
 * server poziva kao autoritet:
 *  - validate() odlucuje koji su slotovi dostupni za izbor. Server vraca prosle
 *    termine danasnjeg dana kao slobodne, jer zna samo za zauzetost, ne za vreme;
 *  - defaultSlots() odredjuje koliko se bira jednim dodirom (sto ceo dan, sala sat);
 *  - validate() se ponovo poziva neposredno pre slanja, nad celim izborom.
 *
 * Klijentska provera sluzi brzini odziva interfejsa. Garanciju da se termini ne
 * preklapaju daje iskljucivo jedinstveni indeks u bazi - zato je 409 uvek moguc,
 * i obradjuje se kao normalan ishod.
 */
class AvailabilityViewModel(private val resource: ResourceDto) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<AvailabilityState> = _state.asStateFlow()

    init {
        load()
    }

    /** Pri svakom ulasku na ekran: svez prikaz, bez poruke iz prethodne posete. */
    fun onEnter() {
        _state.update { it.copy(notice = null, selection = null) }
        load()
    }

    fun previousDay() = changeDay(-DAY)

    fun nextDay() = changeDay(+DAY)

    fun onSlotTap(index: Int) {
        val s = _state.value
        val now = System.currentTimeMillis()
        if (!selectable(s.slots, index, now)) return

        val sel = s.selection
        val novi = when {
            sel == null -> defaultRangeFrom(s.slots, index, now)
            index == sel.first -> null
            index > sel.first && (sel.first..index).all { selectable(s.slots, it, now) } -> sel.first..index
            else -> defaultRangeFrom(s.slots, index, now)
        }
        _state.update { it.copy(selection = novi, notice = null, cells = cellsFor(it.slots, novi, now)) }
    }

    fun book() {
        val s = _state.value
        val sel = s.selection ?: return
        val start = s.slots[sel.first].startTime
        val end = s.slots[sel.last].endTime

        // Ista provera kao na serveru, pre nego sto zahtev uopste ode na mrezu.
        val provera = BookingRules.validate(start, end, System.currentTimeMillis())
        if (provera is BookingRules.Result.Invalid) {
            _state.update { it.copy(notice = Notice(provera.message, isError = true)) }
            load()
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isBooking = true, notice = null) }
            val notice = try {
                when (val ishod = ApiClient.book(CreateBookingRequest(resource.id, start, end))) {
                    is ApiClient.BookingOutcome.Ok ->
                        Notice("Rezervisano: ${TimeFormat.hhmm(start)}-${TimeFormat.hhmm(end)}", isError = false)

                    ApiClient.BookingOutcome.Taken ->
                        Notice("Termin je upravo zauzet - neko je bio brzi. Mreza je osvezena.", isError = true)

                    is ApiClient.BookingOutcome.Rejected ->
                        Notice(ishod.message, isError = true)
                }
            } catch (e: Exception) {
                Notice(
                    "Server nije dostupan na ${ApiClient.baseUrl}. " +
                        "Proveri da li je pokrenut i da li adresa odgovara uredjaju.",
                    isError = true
                )
            }
            _state.update { it.copy(isBooking = false, notice = notice, selection = null) }
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            try {
                val av = ApiClient.availability(resource.id, _state.value.dayAnchor)
                val now = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        isLoading = false,
                        slots = av.slots,
                        selection = null,
                        cells = cellsFor(av.slots, null, now)
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        loadError = "Server nije dostupan na ${ApiClient.baseUrl}. " +
                            "Proveri da li je pokrenut i da li adresa odgovara uredjaju."
                    )
                }
            }
        }
    }

    // --- interno ---

    private fun changeDay(delta: Long) {
        val noviDan = _state.value.dayAnchor + delta
        val danas = BookingRules.dayStart(System.currentTimeMillis())
        if (BookingRules.dayStart(noviDan) < danas) return
        _state.update {
            it.copy(
                dayAnchor = noviDan,
                canGoBack = BookingRules.dayStart(noviDan) > danas,
                selection = null,
                notice = null
            )
        }
        load()
    }

    private fun selectable(slots: List<SlotDto>, i: Int, now: Long): Boolean {
        val s = slots.getOrNull(i) ?: return false
        return s.isAvailable &&
            BookingRules.validate(s.startTime, s.endTime, now) is BookingRules.Result.Valid
    }

    /** Od dodirnutog slota bira podrazumevanu duzinu za tip resursa, do prvog zauzetog. */
    private fun defaultRangeFrom(slots: List<SlotDto>, i: Int, now: Long): IntRange {
        val n = BookingRules.defaultSlots(resource.type)
        var end = i
        while (end + 1 < slots.size && end + 1 - i < n && selectable(slots, end + 1, now)) end++
        return i..end
    }

    private fun cellsFor(slots: List<SlotDto>, sel: IntRange?, now: Long): List<SlotCell> =
        slots.mapIndexed { i, slot ->
            val kind = when {
                sel != null && i in sel -> SlotKind.SELECTED
                !slot.isAvailable -> SlotKind.TAKEN
                BookingRules.validate(slot.startTime, slot.endTime, now) !is BookingRules.Result.Valid -> SlotKind.BLOCKED
                else -> SlotKind.FREE
            }
            SlotCell(slot, kind)
        }

    private companion object {
        const val DAY = 86_400_000L

        /** Ako za danas nije ostao nijedan slot, mreza se otvara na sutrasnjem danu. */
        fun initialState(): AvailabilityState {
            val now = System.currentTimeMillis()
            val poslednjiSlot = BookingRules.dayStart(now) +
                (BookingRules.slotsPerDay() - 1) * BookingRules.SLOT_MILLIS
            val dan = if (now >= poslednjiSlot) now + DAY else now
            return AvailabilityState(
                dayAnchor = dan,
                canGoBack = BookingRules.dayStart(dan) > BookingRules.dayStart(now)
            )
        }
    }
}
