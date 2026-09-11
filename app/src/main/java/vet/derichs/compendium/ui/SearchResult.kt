package vet.derichs.compendium.ui

import vet.derichs.compendium.data.model.Medication

data class SearchResult(
    val direct: List<Medication>,  // tiers 1-4 on name + composition matches
    val fuzzy: List<Medication>    // Jaro-Winkler ≥ 0.85, shown below a divider
) {
    val isEmpty: Boolean get() = direct.isEmpty() && fuzzy.isEmpty()
    val hasFuzzy: Boolean get() = fuzzy.isNotEmpty()

    companion object {
        val EMPTY = SearchResult(emptyList(), emptyList())
    }
}
