package com.wolfeleo2.subsy.ui.util

import com.wolfeleo2.subsy.data.catalog.PresetServiceCatalog

object BrandLogoResolver {
    private val KNOWN_DOMAINS =
        mapOf(
            "netflix" to "netflix.com",
            "spotify" to "spotify.com",
            "youtube" to "youtube.com",
            "youtube premium" to "youtube.com",
            "disney+" to "disneyplus.com",
            "disney plus" to "disneyplus.com",
            "apple" to "apple.com",
            "apple tv+" to "apple.com",
            "apple music" to "apple.com",
            "icloud" to "apple.com",
            "chatgpt" to "openai.com",
            "chatgpt plus" to "openai.com",
            "claude" to "anthropic.com",
            "claude pro" to "anthropic.com",
            "github" to "github.com",
            "github copilot" to "github.com",
            "amazon" to "amazon.com",
            "amazon prime" to "amazon.com",
            "prime video" to "amazon.com",
            "notion" to "notion.so",
            "figma" to "figma.com",
            "slack" to "slack.com",
            "dropbox" to "dropbox.com",
            "playstation" to "playstation.com",
            "playstation plus" to "playstation.com",
            "xbox" to "xbox.com",
            "xbox game pass" to "xbox.com",
            "nintendo" to "nintendo.com",
            "nintendo switch online" to "nintendo.com",
            "duolingo" to "duolingo.com",
            "new york times" to "nytimes.com",
            "nyt" to "nytimes.com",
            "wall street journal" to "wsj.com",
            "wsj" to "wsj.com",
        )

    fun resolveDomain(
        name: String,
        cancellationUrl: String? = null,
    ): String? {
        if (!cancellationUrl.isNullOrBlank()) {
            val extracted = extractDomainFromUrl(cancellationUrl)
            if (extracted != null) return extracted
        }

        val preset =
            PresetServiceCatalog.presets.find {
                it.name.equals(name, ignoreCase = true) ||
                    it.keywords.any { kw -> kw.equals(name, ignoreCase = true) }
            }
        if (preset != null && preset.cancellationUrl.isNotBlank()) {
            val extracted = extractDomainFromUrl(preset.cancellationUrl)
            if (extracted != null) return extracted
        }

        val cleanedName = name.trim().lowercase()
        val known = KNOWN_DOMAINS[cleanedName]
        if (known != null) return known

        if (cleanedName.contains('.') && !cleanedName.contains(' ')) {
            return cleanedName
        }

        // No guessing "<name>.com": a custom "Gym" would show some unrelated site's favicon.
        return null
    }

    fun extractDomainFromUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null
        val withScheme =
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                "https://$trimmed"
            } else {
                trimmed
            }
        return try {
            val host =
                java.net.URI(withScheme).host
                    ?: extractHostFromString(withScheme)
            if (host != null) normalizeHost(host) else null
        } catch (_: Exception) {
            val host = extractHostFromString(withScheme)
            if (host != null) normalizeHost(host) else null
        }
    }

    private fun extractHostFromString(url: String): String? {
        val withoutScheme = url.substringAfter("://")
        val hostPart = withoutScheme.substringBefore('/').substringBefore('?')
        return hostPart.ifBlank { null }
    }

    private fun normalizeHost(rawHost: String): String {
        val cleanHost = rawHost.lowercase().removePrefix("www.")
        val parts = cleanHost.split('.')
        return if (parts.size >= 3 && !cleanHost.endsWith(".co.uk") && !cleanHost.endsWith(".com.au")) {
            parts.takeLast(2).joinToString(".")
        } else {
            cleanHost
        }
    }

    /**
     * Google's favicon service (undocumented, so it may change; a failed load falls back to the initial).
     * A host without a dot is skipped so typing a URL doesn't fire a request per keystroke.
     */
    fun logoUrl(
        name: String,
        cancellationUrl: String? = null,
    ): String? {
        val domain = resolveDomain(name, cancellationUrl)?.takeIf { '.' in it } ?: return null
        return "https://www.google.com/s2/favicons?domain=$domain&sz=128"
    }
}
