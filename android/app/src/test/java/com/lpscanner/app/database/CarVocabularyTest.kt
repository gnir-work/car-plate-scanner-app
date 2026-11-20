package com.lpscanner.app.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CarVocabularyTest {
    @Test fun `Hebrew company names are stored in English`() {
        assertEquals("Toyota", CarVocabulary.toEnglish(OptionKind.Company, "טויוטה"))
        assertEquals("Peugeot", CarVocabulary.toEnglish(OptionKind.Company, "פיג׳ו")) // Hebrew geresh
        assertEquals("Peugeot", CarVocabulary.toEnglish(OptionKind.Company, "פיג'ו")) // ASCII apostrophe
        assertEquals("BMW", CarVocabulary.toEnglish(OptionKind.Company, "במוו"))
        assertEquals("BMW", CarVocabulary.toEnglish(OptionKind.Company, " ב.מ.וו "))
    }

    @Test fun `Hebrew colors in either gender are stored in English`() {
        assertEquals("Red", CarVocabulary.toEnglish(OptionKind.Color, "אדום"))
        assertEquals("Red", CarVocabulary.toEnglish(OptionKind.Color, "אדומה"))
        assertEquals("Silver", CarVocabulary.toEnglish(OptionKind.Color, "כסף"))
    }

    @Test fun `English names get their canonical spelling`() {
        assertEquals("Gray", CarVocabulary.toEnglish(OptionKind.Color, "grey"))
        assertEquals("Volkswagen", CarVocabulary.toEnglish(OptionKind.Company, "VW"))
        assertEquals("Land Rover", CarVocabulary.toEnglish(OptionKind.Company, "land-rover"))
    }

    @Test fun `unknown names are kept as typed`() {
        assertEquals("ורוד מטאלי", CarVocabulary.toEnglish(OptionKind.Color, "  ורוד   מטאלי "))
        assertEquals("Trabant", CarVocabulary.toEnglish(OptionKind.Company, "Trabant"))
    }

    @Test fun `colors and companies don't mix`() {
        assertEquals("זהב", CarVocabulary.toEnglish(OptionKind.Company, "זהב"))
    }

    @Test fun `stored names are shown in Hebrew when known`() {
        assertEquals("טויוטה", CarVocabulary.toHebrew(OptionKind.Company, "Toyota"))
        assertEquals("אפור", CarVocabulary.toHebrew(OptionKind.Color, "grey"))
        assertEquals("Trabant", CarVocabulary.toHebrew(OptionKind.Company, "Trabant"))
    }

    @Test fun `every default option has a Hebrew name`() {
        PlateSheetSchema.DEFAULT_COLORS.forEach { assertNotEquals(it, it, CarVocabulary.toHebrew(OptionKind.Color, it)) }
        PlateSheetSchema.DEFAULT_COMPANIES.forEach { assertNotEquals(it, it, CarVocabulary.toHebrew(OptionKind.Company, it)) }
    }
}
