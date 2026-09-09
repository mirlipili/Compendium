package vet.derichs.compendium.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import vet.derichs.compendium.R
import vet.derichs.compendium.data.model.Medication
import vet.derichs.compendium.ui.DataStatus
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationListScreen(
    medications: List<Medication>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onMedicationClick: (Medication) -> Unit,
    onRefreshClick: () -> Unit = {},
    isLoading: Boolean = false,
    refreshMessage: String? = null,
    currentLanguage: String = "fr",
    supportedLanguages: List<String> = emptyList(),
    onLanguageClick: (String) -> Unit = {},
    getLanguageDisplayName: (String) -> String = { it },
    getOtherLanguageShortName: () -> String = { "NL" },
    navigateToGeneralNotes: () -> Unit,
    onExportNotes: () -> Unit = {},
    dataStatus: DataStatus? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text(stringResource(R.string.search_medications)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                enabled = !isLoading,
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            MedicationMenu(
                onRefreshClick = onRefreshClick,
                onLanguageClick = onLanguageClick,
                onExportNotes = onExportNotes,
                isLoading = isLoading,
                otherLanguageShortName = getOtherLanguageShortName()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        refreshMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.loading_medications))
                    }
                }
            }

            medications.isEmpty() && searchQuery.isBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.no_medications_found),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.try_refresh),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            medications.isEmpty() && searchQuery.isNotBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.no_results_for, searchQuery),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { DataStatusRow(dataStatus) }

                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navigateToGeneralNotes() },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.general_notes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.view_or_edit_notes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    items(medications) { medication ->
                        MedicationItem(medication = medication, onClick = { onMedicationClick(medication) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DataStatusRow(dataStatus: DataStatus?) {
    val now = System.currentTimeMillis()
    val thirtyDaysMs = TimeUnit.DAYS.toMillis(30)

    val isNeverChecked = dataStatus == null || dataStatus.lastCheckedAt == 0L
    val isStale = !isNeverChecked && (now - dataStatus!!.lastCheckedAt) > thirtyDaysMs

    when {
        isNeverChecked -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.data_status_never),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        isStale -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.data_status_stale),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        else -> {
            val daysSinceCheck = TimeUnit.MILLISECONDS.toDays(now - dataStatus!!.lastCheckedAt)
            val checkedLabel = when (daysSinceCheck) {
                0L -> stringResource(R.string.data_status_today)
                1L -> stringResource(R.string.data_status_yesterday)
                else -> stringResource(R.string.data_status_days_ago, daysSinceCheck)
            }
            Text(
                text = stringResource(R.string.data_status, dataStatus.dataPublishedAt, checkedLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun MedicationMenu(
    onRefreshClick: () -> Unit,
    onLanguageClick: (String) -> Unit,
    onExportNotes: () -> Unit,
    isLoading: Boolean,
    otherLanguageShortName: String
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }, enabled = !isLoading) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.refresh_data))
                    }
                },
                onClick = { expanded = false; onRefreshClick() }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(otherLanguageShortName)
                    }
                },
                onClick = { expanded = false; onLanguageClick("") }
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.export_notes))
                    }
                },
                onClick = { expanded = false; onExportNotes() }
            )
        }
    }
}

@Composable
private fun MedicationItem(medication: Medication, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = medication.name ?: "Unknown Medication",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            medication.composition?.let { composition ->
                Text(
                    text = composition,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            medication.firm?.let { firm ->
                Text(
                    text = firm,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
