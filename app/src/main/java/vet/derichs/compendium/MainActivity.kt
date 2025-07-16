package vet.derichs.compendium

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import vet.derichs.compendium.data.model.Medication
import vet.derichs.compendium.ui.MedicationViewModel
import vet.derichs.compendium.ui.screens.MedicationDetailScreen
import vet.derichs.compendium.ui.screens.MedicationListScreen
import vet.derichs.compendium.ui.theme.VetCompendiumTheme
import vet.derichs.compendium.utils.LanguageManager

class MainActivity : ComponentActivity() {
    private val medicationViewModel: MedicationViewModel by viewModels()
    private lateinit var languageManager: LanguageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the language manager
        languageManager = LanguageManager(this)

        setContent {
            VetCompendiumTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VetCompendiumApp(
                        viewModel = medicationViewModel,
                        onRecreateActivity = {
                            recreate() // Recreate the activity to apply language changes
                            medicationViewModel.onActivityRecreated()
                        }
                    )
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        if (newBase != null) {
            val languageManager = LanguageManager(newBase)
            val currentLanguage = languageManager.getCurrentLanguage()
            val context = languageManager.applyLanguageToContext(newBase, currentLanguage)
            super.attachBaseContext(context)
        } else {
            super.attachBaseContext(newBase)
        }
    }
}

@Composable
private fun VetCompendiumApp(
    viewModel: MedicationViewModel,
    onRecreateActivity: () -> Unit
) {
    val navController = rememberNavController()

    // Collect states
    val medications by viewModel.medications.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val refreshMessage by viewModel.refreshMessage.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val shouldRecreateActivity by viewModel.shouldRecreateActivity.collectAsState()

    var selectedMedication by remember { mutableStateOf<Medication?>(null) }

    // Listen for activity recreation requests
    LaunchedEffect(shouldRecreateActivity) {
        if (shouldRecreateActivity) {
            onRecreateActivity()
        }
    }

    NavHost(
        navController = navController,
        startDestination = "medication_list"
    ) {
        composable("medication_list") {
            MedicationListScreen(
                medications = medications,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onMedicationClick = { medication ->
                    selectedMedication = medication
                    navController.navigate("medication_detail")
                },
                onRefreshClick = {
                    viewModel.refreshData()
                },
                isLoading = isLoading,
                refreshMessage = refreshMessage,
                currentLanguage = currentLanguage,
                supportedLanguages = viewModel.getSupportedLanguages(),
                onLanguageClick = { _ ->
                    // Switch to the other language
                    viewModel.switchToOtherLanguage()
                },
                getLanguageDisplayName = viewModel::getLanguageDisplayName,
                getOtherLanguageShortName = viewModel::getOtherLanguageShortName,
                onExportNotes = {
                    viewModel.exportNotes()
                }
            )
        }

        composable("medication_detail") {
            MedicationDetailScreen(
                medication = selectedMedication,
                onBackClick = {
                    navController.popBackStack()
                },
                viewModel = viewModel
            )
        }
    }
}
