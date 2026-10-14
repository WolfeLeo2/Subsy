package com.wolfeleo2.subsy.data

import com.wolfeleo2.subsy.data.catalog.PresetServiceCatalog
import com.wolfeleo2.subsy.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetServiceCatalogTest {
    @Test
    fun catalogContainsAtLeastFiftyPresets() {
        val totalPresets = PresetServiceCatalog.presets.size
        assertTrue("Catalog should contain at least 50 presets, found $totalPresets", totalPresets >= 50)
    }

    @Test
    fun allPresetsHaveValidMetadata() {
        val hexRegex = Regex("^#[0-9A-Fa-f]{6}$")

        PresetServiceCatalog.presets.forEach { preset ->
            assertTrue("Preset ${preset.id} should have a non-blank name", preset.name.isNotBlank())
            assertTrue("Preset ${preset.id} should have a positive amount", preset.defaultAmount > 0.0)
            assertTrue(
                "Preset ${preset.id} colorHex '${preset.colorHex}' must be valid 6-char hex",
                hexRegex.matches(preset.colorHex),
            )
            assertTrue(
                "Preset ${preset.id} should have a valid cancellation URL starting with http",
                preset.cancellationUrl.startsWith("http"),
            )
            assertTrue(
                "Preset ${preset.id} should have an icon name",
                preset.iconName.isNotBlank(),
            )
        }
    }

    @Test
    fun findByIdReturnsCorrectPreset() {
        val netflix = PresetServiceCatalog.findById("netflix")
        assertNotNull(netflix)
        assertEquals("Netflix", netflix?.name)
        assertEquals(Category.ENTERTAINMENT, netflix?.category)

        val spotify = PresetServiceCatalog.findById("spotify")
        assertNotNull(spotify)
        assertEquals("Spotify", spotify?.name)
    }

    @Test
    fun searchFindsPresetsByNameAndKeyword() {
        val openaiResults = PresetServiceCatalog.search("openai")
        assertTrue(openaiResults.any { it.name == "ChatGPT Plus" })

        val videoResults = PresetServiceCatalog.search("video")
        assertTrue(videoResults.isNotEmpty())

        val appleResults = PresetServiceCatalog.search("apple")
        assertTrue(appleResults.size >= 3)
    }

    @Test
    fun getByCategoryFiltersCorrectly() {
        val entertainment = PresetServiceCatalog.getByCategory(Category.ENTERTAINMENT)
        assertTrue(entertainment.isNotEmpty())
        assertTrue(entertainment.all { it.category == Category.ENTERTAINMENT })

        val workSaaS = PresetServiceCatalog.getByCategory(Category.WORK_SAAS)
        assertTrue(workSaaS.isNotEmpty())
        assertTrue(workSaaS.all { it.category == Category.WORK_SAAS })
    }
}
