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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import vet.derichs.compendium.ui.MedicationViewModel
import vet.derichs.compendium.ui.screens.MedicationDetailScreen
import vet.derichs.compendium.ui.screens.MedicationListScreen
import vet.derichs.compendium.ui.theme.VetCompendiumTheme
import vet.derichs.compendium.ui.notes.GeneralNotesScreen
import vet.derichs.compendium.utils.LanguageManager
import vet.derichs.compendium.worker.UpdateWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val medicationViewModel: MedicationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleBackgroundUpdates()
        setContent {
            VetCompendiumTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VetCompendiumApp(
                        viewModel = medicationViewModel,
                        onRecreate = {
                            medicationViewModel.onActivityRecreated()
                            recreate()
                        }
                    )
                }
            }
        }
    }

    private fun scheduleBackgroundUpdates() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS, 3, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        // KEEP: if the work is already scheduled from a previous launch, leave it as-is
        // so we don't reset the 24-hour timer every time the app opens.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            UpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
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
private fun VetCompendiumApp(viewModel: MedicationViewModel, onRecreate: () -> Unit) {
    val navController = rememberNavController()

    val medications by viewModel.medications.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isInitializing by viewModel.isInitializing.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val refreshMessage by viewModel.refreshMessage.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val dataStatus by viewModel.dataStatus.collectAsState()
    val fuzzyMedications by viewModel.fuzzyMedications.collectAsState()
    val shouldRecreateActivity by viewModel.shouldRecreateActivity.collectAsState()

    LaunchedEffect(shouldRecreateActivity) {
        if (shouldRecreateActivity) onRecreate()
    }

    NavHost(navController = navController, startDestination = "medication_list") {
        composable("medication_list") {
            MedicationListScreen(
                medications = medications,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onMedicationClick = { medication ->
                    navController.navigate("medication_detail/${medication.id}")
                },
                onRefreshClick = viewModel::refreshData,
                isInitializing = isInitializing,
                isRefreshing = isRefreshing,
                refreshMessage = refreshMessage,
                currentLanguage = currentLanguage,
                supportedLanguages = viewModel.getSupportedLanguages(),
                onLanguageClick = { _ -> viewModel.switchToOtherLanguage() },
                getLanguageDisplayName = viewModel::getLanguageDisplayName,
                getOtherLanguageShortName = viewModel::getOtherLanguageShortName,
                onExportNotes = viewModel::exportNotes,
                navigateToGeneralNotes = { navController.navigate("generalNotes") },
                dataStatus = dataStatus,
                fuzzyMedications = fuzzyMedications
            )
        }

        composable("generalNotes") {
            GeneralNotesScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "medication_detail/{medicationId}",
            arguments = listOf(navArgument("medicationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val medicationId = backStackEntry.arguments?.getString("medicationId")
            val medication = medications.find { it.id == medicationId }

            LaunchedEffect(medication, isInitializing) {
                if (medication == null && !isInitializing && medications.isNotEmpty()) {
                    navController.popBackStack("medication_list", false)
                }
            }

            MedicationDetailScreen(
                medication = medication,
                onBackClick = { navController.popBackStack() },
                viewModel = viewModel
            )
        }
    }
}
