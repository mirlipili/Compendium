package vet.derichs.compendium.ui

data class DataStatus(
    val dataVersion: Long,
    val dataPublishedAt: String,
    val lastCheckedAt: Long  // epoch ms; 0 = never successfully checked
)
