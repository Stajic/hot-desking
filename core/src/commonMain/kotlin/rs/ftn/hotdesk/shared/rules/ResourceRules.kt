package rs.ftn.hotdesk.shared.rules

import rs.ftn.hotdesk.shared.model.ResourceType
import rs.ftn.hotdesk.shared.model.UpsertResourceRequest

/**
 * Pravila za resurs koji administrator dodaje ili menja.
 *
 * Isti obrazac kao [BookingRules]: na klijentu forma odmah kaze sta ne valja, bez
 * odlaska na mrezu, a server istu proveru izvrsava kao autoritet, pre upisa u bazu.
 *
 * Bez ove provere server bi prihvatio sto sa osam mesta ili prazan naziv, a naziv
 * duzi od kolone u bazi odbio bi tek Exposed - porukom na engleskom, iz koje klijent
 * ne zna koje polje da ispravi.
 *
 * Granice duzine prate sirinu kolona u Tables.kt: resources.name i resources.location
 * su varchar(120), resource_amenities.amenity je varchar(60). Ako se kolona menja,
 * menja se i granica ovde.
 */
object ResourceRules {

    const val NAME_MAX = 120
    const val LOCATION_MAX = 120
    const val AMENITY_MAX = 60
    const val MAX_AMENITIES = 10

    /** Sala je za sastanak, pa ima bar dva mesta. Gornja granica je velika sala, ne amfiteatar. */
    const val ROOM_MIN_CAPACITY = 2
    const val ROOM_MAX_CAPACITY = 100

    sealed interface Result {
        data object Valid : Result

        /** [code] imenuje polje (NAME, LOCATION, CAPACITY, AMENITIES), da forma zna sta da oznaci. */
        data class Invalid(val code: String, val message: String) : Result
    }

    fun validate(req: UpsertResourceRequest): Result {
        if (req.name.isBlank()) {
            return Result.Invalid("NAME", "Naziv je obavezan.")
        }
        if (req.name.length > NAME_MAX) {
            return Result.Invalid("NAME", "Naziv moze imati najvise $NAME_MAX znakova.")
        }
        if (req.location.isBlank()) {
            return Result.Invalid("LOCATION", "Lokacija je obavezna.")
        }
        if (req.location.length > LOCATION_MAX) {
            return Result.Invalid("LOCATION", "Lokacija moze imati najvise $LOCATION_MAX znakova.")
        }

        when (req.type) {
            // Sto je hot-desking mesto za jednu osobu; vise mesta je sala.
            ResourceType.DESK -> if (req.capacity != 1) {
                return Result.Invalid("CAPACITY", "Sto je za jednu osobu, pa kapacitet mora biti 1.")
            }
            ResourceType.MEETING_ROOM -> if (req.capacity !in ROOM_MIN_CAPACITY..ROOM_MAX_CAPACITY) {
                return Result.Invalid(
                    "CAPACITY",
                    "Kapacitet sale mora biti od $ROOM_MIN_CAPACITY do $ROOM_MAX_CAPACITY."
                )
            }
        }

        if (req.amenities.size > MAX_AMENITIES) {
            return Result.Invalid("AMENITIES", "Resurs moze imati najvise $MAX_AMENITIES stavki opreme.")
        }
        if (req.amenities.any { it.isBlank() }) {
            return Result.Invalid("AMENITIES", "Stavka opreme ne sme biti prazna.")
        }
        if (req.amenities.any { it.length > AMENITY_MAX }) {
            return Result.Invalid("AMENITIES", "Stavka opreme moze imati najvise $AMENITY_MAX znakova.")
        }

        return Result.Valid
    }
}
