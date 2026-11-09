package pt.nobreride.copilot.radar

import java.util.Locale

data class ParsedOffer(
    val price: Double?,
    val distanceKm: Double?,
    val durationMinutes: Int?,
    val eurosPerKm: Double?,
    val eurosPerHour: Double?
) {
    fun summary(): String {
        val parts = mutableListOf<String>()
        price?.let { parts += "Preço: ${money(it)}" }
        distanceKm?.let { parts += "Distância: ${String.format(Locale.US, "%.1f", it)} km" }
        durationMinutes?.let { parts += "Duração: ${it} min" }
        eurosPerKm?.let { parts += "${String.format(Locale.US, "%.2f", it)} €/km" }
        eurosPerHour?.let { parts += "${String.format(Locale.US, "%.2f", it)} €/h" }
        return if (parts.isEmpty()) "Oferta ainda não identificada" else parts.joinToString("  •  ")
    }

    private fun money(value: Double): String =
        String.format(Locale.forLanguageTag("pt-PT"), "%.2f €", value)
}

object OfferDataParser {
    private val priceRegex = Regex("""(?:€\s*)?(\d{1,3}(?:[.,]\d{1,2})?)\s*€""")
    private val distanceRegex = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s*km\b""", RegexOption.IGNORE_CASE)
    private val hoursMinutesRegex = Regex("""(\d{1,2})\s*h(?:oras?)?\s*(?:(\d{1,2})\s*min(?:utos?)?)?""", RegexOption.IGNORE_CASE)
    private val minutesRegex = Regex("""(\d{1,3})\s*min(?:utos?)?\b""", RegexOption.IGNORE_CASE)

    fun parse(text: String): ParsedOffer {
        val normalized = text.replace('\u00A0', ' ')
        val priceMatch = priceRegex.find(normalized)
        val priceText = priceMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
        val price = priceText?.replace(',', '.')?.toDoubleOrNull()
        val distance = distanceRegex.find(normalized)?.groupValues?.get(1)
            ?.replace(',', '.')?.toDoubleOrNull()

        val hourMatch = hoursMinutesRegex.find(normalized)
        val duration = if (hourMatch != null) {
            val hours = hourMatch.groupValues[1].toIntOrNull() ?: 0
            val minutes = hourMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            hours * 60 + minutes
        } else {
            minutesRegex.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
        }

        val perKm = if (price != null && distance != null && distance > 0) price / distance else null
        val perHour = if (price != null && duration != null && duration > 0) price * 60.0 / duration else null

        return ParsedOffer(price, distance, duration, perKm, perHour)
    }
}
