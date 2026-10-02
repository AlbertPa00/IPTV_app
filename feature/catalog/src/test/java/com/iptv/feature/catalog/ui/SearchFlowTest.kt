package com.iptv.feature.catalog.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchFlowTest {

    data class Filters(val query: String = "")

    @Test
    fun debouncedQueryReachesCombine() = runBlocking {
        val filters = MutableStateFlow(Filters())
        val debouncedQuery = filters.map { it.query }
            .distinctUntilChanged()
            .debounce { if (it.isBlank()) 0L else 300L }

        val seen = mutableListOf<String>()
        val job = launch {
            combine(flowOf("src"), filters, debouncedQuery) { s, f, q ->
                s to f.copy(query = q)
            }
                .distinctUntilChanged()
                .collectLatest { seen += it.second.query }
        }

        // Espera activa a cada emisión: con delay fijo, un scheduler lento
        // podía dejar que "espn" cancelara el debounce pendiente de "".
        suspend fun awaitSeen(expected: List<String>) {
            val deadline = System.currentTimeMillis() + 2_000
            while (seen != expected && System.currentTimeMillis() < deadline) delay(10)
        }

        awaitSeen(listOf(""))
        filters.value = Filters("espn")
        awaitSeen(listOf("", "espn"))
        filters.value = Filters("hbo")
        awaitSeen(listOf("", "espn", "hbo"))
        job.cancel()

        assertEquals(listOf("", "espn", "hbo"), seen)
    }
}
