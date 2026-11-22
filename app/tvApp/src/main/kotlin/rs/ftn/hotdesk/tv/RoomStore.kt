package rs.ftn.hotdesk.tv

import android.content.Context

/** Sala koju ovaj ekran prikazuje. */
data class Room(val id: String, val name: String, val location: String)

/**
 * Izabrana sala, zapamcena na uredjaju.
 *
 * Ekran visi pored jedne sale i radi bez nadzora: posle restarta mora odmah da prikaze
 * istu salu, bez ponovnog biranja. SharedPreferences su dovoljne za tri vrednosti i ne
 * traze novu biblioteku.
 */
class RoomStore(context: Context) {

    private val prefs = context.getSharedPreferences("ekran-sale", Context.MODE_PRIVATE)

    fun load(): Room? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        return Room(id, prefs.getString(KEY_NAME, "").orEmpty(), prefs.getString(KEY_LOCATION, "").orEmpty())
    }

    fun save(room: Room) {
        prefs.edit()
            .putString(KEY_ID, room.id)
            .putString(KEY_NAME, room.name)
            .putString(KEY_LOCATION, room.location)
            .apply()
    }

    private companion object {
        const val KEY_ID = "id"
        const val KEY_NAME = "naziv"
        const val KEY_LOCATION = "lokacija"
    }
}
