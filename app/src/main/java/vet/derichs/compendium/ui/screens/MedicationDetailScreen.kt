package vet.derichs.compendium.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import vet.derichs.compendium.R
import vet.derichs.compendium.data.model.Medication
import vet.derichs.compendium.ui.MedicationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDetailScreen(
    medication: Medication?,
    onBackClick: () -> Unit,
    viewModel: MedicationViewModel = viewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(medication?.name ?: "Medication Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (medication == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Medication not found")
            }
        } else {
            MedicationDetailContent(
                medication = medication,
                viewModel = viewModel,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun MedicationDetailContent(
    medication: Medication,
    viewModel: MedicationViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Medication Name Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = medication.name ?: "Unknown Medication",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                medication.composition?.let { composition ->
                    Text(
                        text = composition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                medication.firm?.let { firm ->
                    Text(
                        text = "Firm: $firm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Usage Information Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.usage_information),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                medication.administration_route?.let {
                    DetailItem(stringResource(R.string.administration_route), it)
                }
                medication.posology?.let {
                    DetailItem(stringResource(R.string.posology), it)
                }
                medication.withdrawal_period?.let {
                    DetailItem(stringResource(R.string.withdrawal_period), it)
                }
                medication.packaging?.let {
                    DetailItem(stringResource(R.string.packaging), it)
                }
                medication.prescription?.let {
                    DetailItem(stringResource(R.string.prescription), it)
                }
                medication.rcp_link?.let { link ->
                    ClickableLinkItem(stringResource(R.string.rcp_link), link) { url ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Handle error if URL can't be opened
                        }
                    }
                }
            }
        }

        // Additional Information Card
        if (medication.target_species != null ||
            medication.pharmaceutical_form != null ||
            medication.registration_number != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Additional Information",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    medication.target_species?.let {
                        DetailItem("Target Species", it)
                    }
                    medication.pharmaceutical_form?.let {
                        DetailItem("Pharmaceutical Form", it)
                    }
                    medication.registration_number?.let {
                        DetailItem("Registration Number", it)
                    }
                }
            }
        }

        // Notes Card
        NotesCard(
            medicationId = medication.id,
            viewModel = viewModel
        )
    }
}

@Composable
private fun NotesCard(
    medicationId: String,
    viewModel: MedicationViewModel
) {
    var notesText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // Load existing notes from database
    val savedNotes by viewModel.getNotesForMedication(medicationId).collectAsState(initial = "")

    // Update local state when saved notes change
    LaunchedEffect(savedNotes) {
        notesText = savedNotes
        isLoading = false
    }

    // Debounced save function
    var saveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun saveNotesDebounced(text: String) {
        saveJob?.cancel()
        saveJob = coroutineScope.launch {
            delay(1000) // Wait 1 second after user stops typing
            viewModel.saveNote(medicationId, text)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.notes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = notesText,
                onValueChange = { newText ->
                    notesText = newText
                    saveNotesDebounced(newText)
                },
                label = { Text(stringResource(R.string.add_notes_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = Int.MAX_VALUE,
                enabled = !isLoading
            )

            if (notesText.isNotEmpty()) {
                Text(
                    text = "Last modified: ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailItem(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun ClickableLinkItem(
    label: String,
    url: String,
    onClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        TextButton(
            onClick = { onClick(url) },
            modifier = Modifier.padding(top = 2.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = "Open RCP Document",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start
            )
        }
    }
}
