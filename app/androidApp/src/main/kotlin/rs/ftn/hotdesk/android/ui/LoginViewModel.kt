package rs.ftn.hotdesk.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.ftn.hotdesk.android.data.ApiClient
import rs.ftn.hotdesk.android.data.Session

data class LoginState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting
}

/**
 * Prijava. Isti unidirekcioni tok kao [ResourceListViewModel]: ekran cita stanje,
 * a namere salje nazad kroz metode.
 *
 * Uspesna prijava ne menja stanje ovog ekrana nego [Session]; MainActivity to
 * posmatra i sam prelazi na listu resursa.
 */
class LoginViewModel : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        if (!_state.value.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            try {
                when (val ishod = ApiClient.login(_state.value.email, _state.value.password)) {
                    is ApiClient.LoginOutcome.Ok -> {
                        _state.update { it.copy(isSubmitting = false, password = "") }
                        Session.start(ishod.response)
                    }

                    ApiClient.LoginOutcome.BadCredentials ->
                        _state.update {
                            it.copy(isSubmitting = false, error = "Neispravan email ili lozinka.")
                        }

                    is ApiClient.LoginOutcome.Failed ->
                        _state.update { it.copy(isSubmitting = false, error = ishod.message) }
                }
            } catch (e: Exception) {
                // Ista poruka kao na listi resursa: kaze sta da se uradi, ne samo sta je puklo.
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        error = "Server nije dostupan na ${ApiClient.baseUrl}. " +
                            "Proveri da li je pokrenut i da li adresa odgovara uredjaju."
                    )
                }
            }
        }
    }
}
