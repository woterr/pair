package com.wood.pair.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wood.pair.data.PairGraph

/**
 * Plain constructor injection through a factory.
 *
 * The graph is small and entirely known at composition time, so a DI framework would add build
 * cost and indirection without removing any real complexity.
 */
fun <VM : ViewModel> pairViewModelFactory(
    graph: PairGraph,
    create: (PairGraph) -> VM,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create(graph) as T
}

/**
 * Creates (or retrieves) a [ViewModel] wired from the app's graph.
 *
 * Scoped to the current [androidx.navigation.NavBackStackEntry] by Compose Navigation, so each
 * screen gets its own instance and it survives configuration changes.
 */
@Composable
inline fun <reified VM : ViewModel> rememberPairViewModel(
    graph: PairGraph,
    key: String? = null,
    noinline create: (PairGraph) -> VM,
): VM {
    val factory = remember(graph) { pairViewModelFactory(graph, create) }
    return viewModel(key = key, factory = factory)
}
