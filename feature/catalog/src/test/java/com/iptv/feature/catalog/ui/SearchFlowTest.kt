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

        delay(50)
        filters.value = Filters("espn")
        delay(500)
        filters.value = Filters("hbo")
        delay(500)
        job.cancel()

        assertEquals(listOf("", "espn", "hbo"), seen)
    }
}
