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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class MedicationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MedicationRepository
    private val notesRepository: NotesRepository
    private val languageManager: LanguageManager
    private val notesManager: NotesManager

    companion object {
        private const val TAG = "MedicationViewModel"
    }

    private val _isInitializing = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _refreshMessage = MutableStateFlow<String?>(null)
    private val _currentLanguage = MutableStateFlow("fr")
    private val _generalNote = MutableStateFlow<GeneralNote?>(null)
    private val _dataStatus = MutableStateFlow<DataStatus?>(null)
    private val _shouldRecreateActivity = MutableStateFlow(false)

    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val refreshMessage: StateFlow<String?> = _refreshMessage.asStateFlow()
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()
    val generalNote: StateFlow<GeneralNote?> = _generalNote.asStateFlow()
    val dataStatus: StateFlow<DataStatus?> = _dataStatus.asStateFlow()
    val shouldRecreateActivity: StateFlow<Boolean> = _shouldRecreateActivity.asStateFlow()

    // Single source of truth for search: debounced query + language → ranked SearchResult.
    // Debounce is 0 for a blank query (initial load is immediate) and 200ms for typed queries.
    private val _searchResult: StateFlow<SearchResult> =
        combine(_searchQuery, _currentLanguage) { query, lang -> query to lang }
            .distinctUntilChanged()
            .debounce { (query, _) -> if (query.isBlank()) 0L else 200L }
            .flatMapLatest { (query, lang) ->
                repository.getAllMedications(lang).map { allMeds ->
                    if (query.isBlank()) {
                        SearchResult(allMeds, emptyList())
                    } else {
                        withContext(Dispatchers.Default) {
                            repository.rankSearch(query, allMeds)
                        }
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = SearchResult.EMPTY
            )

    val medications: StateFlow<List<Medication>> = _searchResult
        .map { it.direct }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fuzzyMedications: StateFlow<List<Medication>> = _searchResult
        .map { it.fuzzy }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

        val currentLang = _currentLanguage.value
        viewModelScope.launch {
            try {
                _isInitializing.value = true
                repository.initializePrimaryLanguage(currentLang)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize data", e)
                _refreshMessage.value = "Erreur de chargement : ${e.message}"
                kotlinx.coroutines.delay(5000)
                _refreshMessage.value = null
            } finally {
                _isInitializing.value = false
            }
        }
        // Load the other language in the background — no spinner, non-blocking.
        viewModelScope.launch {
            repository.initializeSecondaryLanguages(currentLang)
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
                // Offline-first: load from DB or assets, no network call.
                val result = repository.ensureLanguageLoaded(language)
                result.fold(
                    onSuccess = { _ ->
                        languageManager.setLanguage(language)
                        _currentLanguage.value = language
                        _dataStatus.value = repository.getDataStatus(language)
                        // Recreate the activity so attachBaseContext() picks up the new
                        // locale and reloads all string resources in the correct language.
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
                val allVisible = _searchResult.value.direct + _searchResult.value.fuzzy
                val result = notesManager.exportNotes(allVisible)
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
