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
    onExportNotes: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search bar with clear button and menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search field with clear button
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text(stringResource(R.string.search_medications)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") }
                        ) {
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
                keyboardActions = KeyboardActions(
                    onSearch = {
                        // Search happens automatically through state
                    }
                ),
                enabled = !isLoading,
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Menu button
            MedicationMenu(
                onRefreshClick = onRefreshClick,
                onLanguageClick = onLanguageClick,
                onExportNotes = onExportNotes,
                isLoading = isLoading,
                otherLanguageShortName = getOtherLanguageShortName()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Show refresh message if available
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
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.loading_medications))
                    }
                }
            }

            medications.isEmpty() && searchQuery.isBlank() -> {
                // Empty state when no search query
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                // Empty search results
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_results_for, searchQuery),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            else -> {
                // Medication list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // --- START: ADDED FOR GENERAL NOTES ---
                    item {
                        // This item uses the exact same Card style as MedicationItem
                        // but with hardcoded text and its own click handler.
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navigateToGeneralNotes() }, // Use the specific callback
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
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
                    // --- END: ADDED FOR GENERAL NOTES ---

                    items(medications) { medication ->
                        MedicationItem(
                            medication = medication,
                            onClick = { onMedicationClick(medication) }
                        )
                    }
                }
            }
        }
    }
}

// ... (MedicationMenu and MedicationItem composables remain unchanged) ...

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
        IconButton(
            onClick = { expanded = true },
            enabled = !isLoading
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.menu)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Refresh option
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.refresh_data))
                    }
                },
                onClick = {
                    expanded = false
                    onRefreshClick()
                }
            )

            // Language switch - show only other language
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Settings, // Use Settings icon instead of Language
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(otherLanguageShortName)
                    }
                },
                onClick = {
                    expanded = false
                    onLanguageClick("")
                }
            )

            HorizontalDivider()

            // Export notes
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.export_notes))
                    }
                },
                onClick = {
                    expanded = false
                    onExportNotes()
                }
            )
        }
    }
}


@Composable
private fun MedicationItem(
    medication: Medication,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
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