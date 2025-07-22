package vet.derichs.compendium.ui.notes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import vet.derichs.compendium.R
import vet.derichs.compendium.ui.MedicationViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralNotesScreen(
    viewModel: MedicationViewModel,
    onNavigateBack: () -> Unit
) {
    val noteState by viewModel.generalNote.collectAsState()

    // Local state to manage the text field without constantly hitting the DB
    var text by remember(noteState) { mutableStateOf(noteState?.content ?: "") }

    // Auto-save when the user leaves the screen
    DisposableEffect(Unit) {
        onDispose {
            // Only save if the text has actually changed
            if (text != (noteState?.content ?: "")) {
                viewModel.updateGeneralNote(text)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.general_notes)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            label = { Text(stringResource(R.string.view_or_edit_notes)) }
        )
    }
}
