package com.wolfeleo2.subsy.ui

import com.wolfeleo2.subsy.ui.util.BrandLogoResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandLogoResolverTest {
    @Test
    fun extractsDomainFromCancellationUrls() {
        val netflix = BrandLogoResolver.extractDomainFromUrl("https://www.netflix.com/youraccount")
        assertEquals("netflix.com", netflix)

        val spotify = BrandLogoResolver.extractDomainFromUrl("https://spotify.com/account/subscription/")
        assertEquals("spotify.com", spotify)

        val apple = BrandLogoResolver.extractDomainFromUrl("https://tv.apple.com")
        assertEquals("apple.com", apple)
    }

    @Test
    fun resolvesDomainFromPresetCatalogName() {
        val domain = BrandLogoResolver.resolveDomain("Netflix")
        assertEquals("netflix.com", domain)

        val disney = BrandLogoResolver.resolveDomain("Disney+")
        assertEquals("disneyplus.com", disney)
    }

    @Test
    fun resolvesDomainFromKnownServices() {
        val chatgpt = BrandLogoResolver.resolveDomain("ChatGPT Plus")
        assertEquals("chatgpt.com", chatgpt)

        val github = BrandLogoResolver.resolveDomain("GitHub Copilot")
        assertEquals("github.com", github)
    }

    @Test
    fun logoUrlUsesGoogleFavicon() {
        assertEquals("https://www.google.com/s2/favicons?domain=netflix.com&sz=128", BrandLogoResolver.logoUrl("Netflix"))
    }

    @Test
    fun partialUrlWithoutDotHasNoLogo() {
        assertNull(BrandLogoResolver.logoUrl("My Custom Gym", "gymmem"))
        assertNull(BrandLogoResolver.logoUrl("My Custom Gym"))
    }

    @Test
    fun customServiceWithUrlResolvesCorrectDomain() {
        val domain = BrandLogoResolver.resolveDomain("My Custom Gym", "https://app.gymmembership.io/settings")
        assertEquals("gymmembership.io", domain)

        val logo = BrandLogoResolver.logoUrl("My Custom Gym", "https://app.gymmembership.io/settings")
        assertTrue(logo!!.contains("gymmembership.io"))
    }
}
