package com.example.vetcompendium

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.vetcompendium.data.model.Medication
import com.example.vetcompendium.ui.MedicationViewModel
import com.example.vetcompendium.ui.screens.MedicationDetailScreen
import com.example.vetcompendium.ui.screens.MedicationListScreen
import com.example.vetcompendium.ui.theme.VetCompendiumTheme
import com.example.vetcompendium.utils.LanguageManager

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
                    VetCompendiumApp(medicationViewModel)
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
    viewModel: MedicationViewModel
) {
    val navController = rememberNavController()
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val refreshMessage by viewModel.refreshMessage.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()

    var selectedMedication by remember { mutableStateOf<Medication?>(null) }

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
