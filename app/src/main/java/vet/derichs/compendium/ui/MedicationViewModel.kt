package vet.derichs.compendium.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import vet.derichs.compendium.data.database.GeneralNoteDao
import vet.derichs.compendium.data.database.MedicationDatabase
import vet.derichs.compendium.data.model.GeneralNote
import vet.derichs.compendium.data.model.Medication
import vet.derichs.compendium.data.repository.MedicationRepository
import vet.derichs.compendium.data.repository.NotesRepository
import vet.derichs.compendium.utils.LanguageManager
import vet.derichs.compendium.utils.NotesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MedicationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MedicationRepository
    private val notesRepository: NotesRepository
    private val languageManager: LanguageManager
    private val notesManager: NotesManager

    companion object {
        private const val TAG = "MedicationViewModel"
    }

    // --- UI State ---
    private val _isLoading = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _refreshMessage = MutableStateFlow<String?>(null)
    private val _currentLanguage = MutableStateFlow("fr")
    private val _shouldRecreateActivity = MutableStateFlow(false)


    // 1. Create a private MutableStateFlow for our note.
    private val _generalNote = MutableStateFlow<GeneralNote?>(null)
    // 2. Expose it as an immutable StateFlow for the UI.
    val generalNote: StateFlow<GeneralNote?> = _generalNote.asStateFlow()


    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val refreshMessage: StateFlow<String?> = _refreshMessage.asStateFlow()
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()
    val shouldRecreateActivity: StateFlow<Boolean> = _shouldRecreateActivity.asStateFlow()

    val medications: StateFlow<List<Medication>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.getAllMedications()
            } else {
                repository.searchMedications(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateGeneralNote(content: String) {
        viewModelScope.launch {
            repository.saveGeneralNote(content)
        }
    }

    init {
        val database = MedicationDatabase.getDatabase(application)

        repository = MedicationRepository(
            medicationDao = database.medicationDao(),
            generalNoteDao = database.generalNoteDao(),
            context = application
        )
        notesRepository = NotesRepository(database.medicationNoteDao())
        languageManager = LanguageManager(application)
        notesManager = NotesManager(application, database.medicationNoteDao())

        languageManager.detectAndSetDefaultLanguage()
        _currentLanguage.value = languageManager.getCurrentLanguage()

        // --- DATA LOADING ---

        viewModelScope.launch {
            repository.generalNote.collect { note ->
                _generalNote.value = note
            }
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.initializeData()
                Log.d(TAG, "Data initialization completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize data", e)
                _refreshMessage.value = "Failed to load initial data: ${e.message}"
                kotlinx.coroutines.delay(5000)
                _refreshMessage.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        Log.d(TAG, "Search query updated: '$query'")
    }

    fun refreshData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _refreshMessage.value = null
                val result = repository.refreshCurrentLanguage()
                result.fold(
                    onSuccess = { message ->
                        _refreshMessage.value = message
                        Log.d(TAG, "Refresh successful: $message")
                    },
                    onFailure = { exception ->
                        _refreshMessage.value = "Refresh failed: ${exception.message}"
                        Log.e(TAG, "Refresh failed", exception)
                    }
                )
                kotlinx.coroutines.delay(3000)
                _refreshMessage.value = null
            } catch (e: Exception) {
                _refreshMessage.value = "Refresh failed: ${e.message}"
                Log.e(TAG, "Error during refresh", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun switchToOtherLanguage() {
        val otherLanguage = languageManager.getOtherLanguage()
        switchLanguage(otherLanguage)
    }

    private fun switchLanguage(language: String) {
        if (language != _currentLanguage.value) {
            viewModelScope.launch {
                try {
                    _isLoading.value = true
                    _refreshMessage.value = null
                    val result = repository.refreshFromServerWithLanguage(language)
                    result.fold(
                        onSuccess = { message ->
                            languageManager.setLanguage(language)
                            _currentLanguage.value = language
                            _refreshMessage.value = message
                            Log.d(TAG, "Language switched to $language: $message")
                            kotlinx.coroutines.delay(1000)
                            _refreshMessage.value = null
                            _shouldRecreateActivity.value = true
                        },
                        onFailure = { exception ->
                            _refreshMessage.value = "Language switch failed: ${exception.message}"
                            Log.e(TAG, "Language switch failed", exception)
                            kotlinx.coroutines.delay(3000)
                            _refreshMessage.value = null
                        }
                    )
                } catch (e: Exception) {
                    _refreshMessage.value = "Language switch failed: ${e.message}"
                    Log.e(TAG, "Error during language switch", e)
                    kotlinx.coroutines.delay(3000)
                    _refreshMessage.value = null
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    fun onActivityRecreated() {
        _shouldRecreateActivity.value = false
    }

    fun exportNotes() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val result = notesManager.exportNotes(medications.value)
                result.fold(
                    onSuccess = { _refreshMessage.value = it },
                    onFailure = { _refreshMessage.value = "Export failed: ${it.message}" }
                )
                kotlinx.coroutines.delay(3000)
                _refreshMessage.value = null
            } catch (e: Exception) {
                _refreshMessage.value = "Export failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveNote(medicationId: String, notes: String) {
        viewModelScope.launch {
            try {
                notesRepository.saveNote(medicationId, notes)
                Log.d(TAG, "Note saved successfully for medication: $medicationId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save note for medication: $medicationId", e)
            }
        }
    }

    fun getNotesForMedication(medicationId: String): Flow<String> {
        return notesRepository.getNoteForMedication(medicationId)
    }

    fun getSupportedLanguages(): List<String> = LanguageManager.SUPPORTED_LANGUAGES

    fun getLanguageDisplayName(language: String): String =
        languageManager.getLanguageDisplayName(language)

    fun getOtherLanguageShortName(): String =
        languageManager.getOtherLanguageShortName()
}
