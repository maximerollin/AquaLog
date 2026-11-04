package com.maximerollin.aqualog.encyclopedia

import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.shared.encyclopedia.Species
import com.maximerollin.aqualog.shared.encyclopedia.decodeSpecies
import org.jetbrains.compose.resources.ExperimentalResourceApi

private var cached: List<Species>? = null

@OptIn(ExperimentalResourceApi::class)
suspend fun loadEncyclopedia(): List<Species> =
    cached ?: decodeSpecies(Res.readBytes("files/encyclopedia/species.json").decodeToString()).also { cached = it }

fun Species.localName(languageTag: String): String = names[languageTag] ?: names["fr"] ?: scientificName
