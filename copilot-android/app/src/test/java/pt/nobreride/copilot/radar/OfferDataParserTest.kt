package pt.nobreride.copilot.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OfferDataParserTest {
    @Test
    fun parsesPortugueseOfferWithEuroPrefixAndDecimalComma() {
        val offer = OfferDataParser.parse("€7,50 • 4,5 km • 12 min")

        assertEquals(7.50, offer.price!!, 0.001)
        assertEquals(4.5, offer.distanceKm!!, 0.001)
        assertEquals(12, offer.durationMinutes)
        assertEquals(1.666, offer.eurosPerKm!!, 0.01)
        assertEquals(37.5, offer.eurosPerHour!!, 0.01)
    }

    @Test
    fun parsesEuroSuffixAndHourDuration() {
        val offer = OfferDataParser.parse("7.50 € | 5 km | 1 h 20 min")

        assertEquals(7.50, offer.price!!, 0.001)
        assertEquals(5.0, offer.distanceKm!!, 0.001)
        assertEquals(80, offer.durationMinutes)
        assertEquals(1.5, offer.eurosPerKm!!, 0.001)
        assertEquals(5.625, offer.eurosPerHour!!, 0.001)
    }

    @Test
    fun leavesMissingFieldsAsUnknown() {
        val offer = OfferDataParser.parse("Oferta disponível agora")

        assertNotNull(offer.summary())
        assertEquals(null, offer.price)
        assertEquals(null, offer.distanceKm)
        assertEquals(null, offer.durationMinutes)
    }
}
