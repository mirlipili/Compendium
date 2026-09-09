package vet.derichs.compendium.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    // Full-screen spinner — only true during first-time database init when the list is empty.
    private val _isInitializing = MutableStateFlow(false)
    // Subtle progress bar — true during network refresh and language switch.
    private val _isRefreshing = MutableStateFlow(false)

    private val _searchQuery = MutableStateFlow("")
    private val _refreshMessage = MutableStateFlow<String?>(null)
    private val _currentLanguage = MutableStateFlow("fr")
    private val _shouldRecreateActivity = MutableStateFlow(false)
    private val _generalNote = MutableStateFlow<GeneralNote?>(null)
    private val _dataStatus = MutableStateFlow<DataStatus?>(null)

    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val refreshMessage: StateFlow<String?> = _refreshMessage.asStateFlow()
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()
    val shouldRecreateActivity: StateFlow<Boolean> = _shouldRecreateActivity.asStateFlow()
    val generalNote: StateFlow<GeneralNote?> = _generalNote.asStateFlow()
    val dataStatus: StateFlow<DataStatus?> = _dataStatus.asStateFlow()

    val medications: StateFlow<List<Medication>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) repository.getAllMedications()
            else repository.searchMedications(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        val database = MedicationDatabase.getDatabase(application)
        repository = MedicationRepository(
            medicationDao = database.medicationDao(),
            generalNoteDao = database.generalNoteDao(),
            context = application,
            cacheDir = application.cacheDir
        )
        notesRepository = NotesRepository(database.medicationNoteDao())
        languageManager = LanguageManager(application)
        notesManager = NotesManager(application, database.medicationNoteDao())

        languageManager.detectAndSetDefaultLanguage()
        _currentLanguage.value = languageManager.getCurrentLanguage()
        _dataStatus.value = repository.getDataStatus(_currentLanguage.value)

        viewModelScope.launch {
            repository.generalNote.collect { note -> _generalNote.value = note }
        }

        viewModelScope.launch {
            try {
                _isInitializing.value = true
                repository.initializeData()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize data", e)
                _refreshMessage.value = "Erreur de chargement : ${e.message}"
                kotlinx.coroutines.delay(5000)
                _refreshMessage.value = null
            } finally {
                _isInitializing.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshData() {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                _refreshMessage.value = null
                val result = repository.refreshCurrentLanguage()
                _dataStatus.value = repository.getDataStatus(_currentLanguage.value)
                result.fold(
                    onSuccess = { message -> _refreshMessage.value = message },
                    onFailure = { exception ->
                        _refreshMessage.value = "Actualisation échouée : ${exception.message}"
                        Log.e(TAG, "Refresh failed", exception)
                    }
                )
                kotlinx.coroutines.delay(3000)
                _refreshMessage.value = null
            } catch (e: Exception) {
                _refreshMessage.value = "Actualisation échouée : ${e.message}"
                Log.e(TAG, "Error during refresh", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun switchToOtherLanguage() {
        switchLanguage(languageManager.getOtherLanguage())
    }

    private fun switchLanguage(language: String) {
        if (language == _currentLanguage.value) return
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                _refreshMessage.value = null
                val result = repository.refreshFromServerWithLanguage(language)
                result.fold(
                    onSuccess = { message ->
                        languageManager.setLanguage(language)
                        _currentLanguage.value = language
                        _dataStatus.value = repository.getDataStatus(language)
                        _refreshMessage.value = message
                        kotlinx.coroutines.delay(1000)
                        _refreshMessage.value = null
                        _shouldRecreateActivity.value = true
                    },
                    onFailure = { exception ->
                        _refreshMessage.value = "Changement de langue échoué : ${exception.message}"
                        Log.e(TAG, "Language switch failed", exception)
                        kotlinx.coroutines.delay(3000)
                        _refreshMessage.value = null
                    }
                )
            } catch (e: Exception) {
                _refreshMessage.value = "Changement de langue échoué : ${e.message}"
                Log.e(TAG, "Error during language switch", e)
                kotlinx.coroutines.delay(3000)
                _refreshMessage.value = null
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun onActivityRecreated() {
        _shouldRecreateActivity.value = false
    }

    fun exportNotes() {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                val result = notesManager.exportNotes(medications.value)
                result.fold(
                    onSuccess = { _refreshMessage.value = it },
                    onFailure = { _refreshMessage.value = "Export échoué : ${it.message}" }
                )
                kotlinx.coroutines.delay(3000)
                _refreshMessage.value = null
            } catch (e: Exception) {
                _refreshMessage.value = "Export échoué : ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun updateGeneralNote(content: String) {
        viewModelScope.launch { repository.saveGeneralNote(content) }
    }

    fun saveNote(medicationId: String, notes: String) {
        viewModelScope.launch {
            try {
                notesRepository.saveNote(medicationId, notes)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save note for medication: $medicationId", e)
            }
        }
    }

    fun getNotesForMedication(medicationId: String): Flow<String> =
        notesRepository.getNoteForMedication(medicationId)

    fun getSupportedLanguages(): List<String> = LanguageManager.SUPPORTED_LANGUAGES
    fun getLanguageDisplayName(language: String): String = languageManager.getLanguageDisplayName(language)
    fun getOtherLanguageShortName(): String = languageManager.getOtherLanguageShortName()
}
