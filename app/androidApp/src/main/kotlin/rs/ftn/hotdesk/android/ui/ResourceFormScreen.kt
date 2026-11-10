package rs.ftn.hotdesk.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import rs.ftn.hotdesk.shared.model.ResourceDto
import rs.ftn.hotdesk.shared.model.ResourceType

/**
 * Forma za dodavanje ([resource] == null) i izmenu resursa (samo ADMIN).
 *
 * [locations] su postojece lokacije, ponudjene kao cipovi ispod polja. Dodir upisuje
 * lokaciju tacno onako kako vec postoji, pa se ista lokacija ne pojavljuje u dva oblika.
 */
@Composable
fun ResourceFormScreen(
    resource: ResourceDto?,
    locations: List<String>,
    onBack: () -> Unit,
    onSaved: (String) -> Unit
) {
    // Jedan ViewModel po resursu, da se pri prelasku na drugi resurs ne vide
    // ni na trenutak polja prethodnog.
    val vm: ResourceFormViewModel = viewModel(key = "resource-form-${resource?.id ?: "novi"}")
    LaunchedEffect(resource) { vm.onEnter(resource) }
    val state by vm.state.collectAsStateWithLifecycle()

    // Sacuvano: forma se zatvara. Naziv se brise pre izlaska, inace bi sledece
    // otvaranje iste forme videlo staru vrednost i odmah se zatvorilo.
    LaunchedEffect(state.savedName) {
        state.savedName?.let {
            vm.savedHandled()
            onSaved(it)
        }
    }

    /** Poruka greske ispod polja, ako se greska odnosi bas na to polje. */
    fun errorFor(field: String): (@Composable () -> Unit)? =
        if (state.errorCode == field) ({ Text(state.errorMessage.orEmpty()) }) else null

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("< Nazad") }
        Text(
            if (resource == null) "Novi resurs" else "Izmena resursa",
            style = MaterialTheme.typography.headlineSmall
        )

        state.notice?.let {
            Text(
                it.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (it.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }

        OutlinedTextField(
            value = state.name,
            onValueChange = vm::setName,
            label = { Text("Naziv") },
            singleLine = true,
            isError = state.errorCode == "NAME",
            supportingText = errorFor("NAME"),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.type == ResourceType.DESK,
                onClick = { vm.setType(ResourceType.DESK) },
                label = { Text("Sto") }
            )
            FilterChip(
                selected = state.type == ResourceType.MEETING_ROOM,
                onClick = { vm.setType(ResourceType.MEETING_ROOM) },
                label = { Text("Sala") }
            )
        }

        OutlinedTextField(
            value = state.location,
            onValueChange = vm::setLocation,
            label = { Text("Lokacija") },
            singleLine = true,
            isError = state.errorCode == "LOCATION",
            supportingText = errorFor("LOCATION"),
            modifier = Modifier.fillMaxWidth()
        )
        if (locations.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                locations.forEach { lokacija ->
                    FilterChip(
                        selected = state.location == lokacija,
                        onClick = { vm.setLocation(lokacija) },
                        label = { Text(lokacija) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.capacity,
            onValueChange = vm::setCapacity,
            label = { Text("Kapacitet") },
            singleLine = true,
            // Sto je uvek za jednu osobu (ResourceRules), pa nema sta da se bira.
            enabled = state.type != ResourceType.DESK,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = state.errorCode == "CAPACITY",
            supportingText = errorFor("CAPACITY"),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.amenities,
            onValueChange = vm::setAmenities,
            label = { Text("Oprema") },
            singleLine = true,
            isError = state.errorCode == "AMENITIES",
            supportingText = errorFor("AMENITIES") ?: { Text("Odvojeno zarezom, npr. projektor, tabla") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = vm::save,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
            } else {
                Text("Sacuvaj")
            }
        }
    }
}
