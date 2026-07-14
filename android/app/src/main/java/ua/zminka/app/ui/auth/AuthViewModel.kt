package ua.zminka.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.zminka.app.data.repository.AuthRepository

enum class AuthMode { LOGIN, SIGNUP }

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(private val repo: AuthRepository = AuthRepository()) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun setMode(mode: AuthMode) = _state.update { it.copy(mode = mode, error = null) }
    fun setEmail(v: String) = _state.update { it.copy(email = v) }
    fun setPassword(v: String) = _state.update { it.copy(password = v) }

    fun submit() {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "Введіть email і пароль") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = if (s.mode == AuthMode.LOGIN) {
                repo.signIn(s.email.trim(), s.password)
            } else {
                repo.signUp(s.email.trim(), s.password)
            }
            result.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Помилка входу") }
            }.onSuccess {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
