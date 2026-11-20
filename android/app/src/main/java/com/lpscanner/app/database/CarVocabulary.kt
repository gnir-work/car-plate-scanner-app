package com.lpscanner.app.database

/**
 * Hebrew ↔ English names for car companies and colors.
 *
 * The sheet always stores English (so it reads the same for every user and sorts/filters
 * consistently); the app shows Hebrew names when the phone is in Hebrew. A name that isn't
 * known here (a custom color or a rare company) is stored exactly as typed.
 *
 * Plain Kotlin, no Android types, so it can be shared with an iOS app later.
 */
object CarVocabulary {
    /** English canonical name → Hebrew names; the first is how it's displayed, the rest are accepted spellings. */
    private val companies: Map<String, List<String>> = linkedMapOf(
        "Toyota" to listOf("טויוטה"),
        "Hyundai" to listOf("יונדאי", "היונדאי"),
        "Kia" to listOf("קיה", "קיא"),
        "Mazda" to listOf("מאזדה", "מזדה"),
        "Skoda" to listOf("סקודה"),
        "Suzuki" to listOf("סוזוקי"),
        "Mitsubishi" to listOf("מיצובישי"),
        "Nissan" to listOf("ניסאן", "ניסן"),
        "Volkswagen" to listOf("פולקסווגן", "פולקסוואגן"),
        "Chevrolet" to listOf("שברולט"),
        "Honda" to listOf("הונדה"),
        "Ford" to listOf("פורד"),
        "Renault" to listOf("רנו"),
        "Peugeot" to listOf("פיג'ו", "פיז'ו", "פג'ו"),
        "Citroen" to listOf("סיטרואן", "סיטרואין"),
        "Seat" to listOf("סיאט"),
        "Subaru" to listOf("סובארו"),
        "Mercedes" to listOf("מרצדס", "מרצדס בנץ"),
        "BMW" to listOf("ב.מ.וו", "במוו", "ב.מ.ו", "בי אם דבליו"),
        "Audi" to listOf("אאודי", "אודי"),
        "Volvo" to listOf("וולוו", "וולבו"),
        "Opel" to listOf("אופל"),
        "Jeep" to listOf("ג'יפ"),
        "Lexus" to listOf("לקסוס"),
        "Dacia" to listOf("דאצ'יה", "דצ'יה"),
        "Tesla" to listOf("טסלה"),
        "BYD" to listOf("בי.וואי.די", "ביואידי"),
        "Geely" to listOf("ג'ילי"),
        "MG" to listOf("אם.ג'י", "אמג'י"),
        "Chery" to listOf("צ'רי"),
        "Fiat" to listOf("פיאט"),
        "Alfa Romeo" to listOf("אלפא רומיאו"),
        "Land Rover" to listOf("לנד רובר"),
        "Porsche" to listOf("פורשה"),
        "Mini" to listOf("מיני"),
        "Cupra" to listOf("קופרה"),
        "Isuzu" to listOf("איסוזו"),
        "Infiniti" to listOf("אינפיניטי"),
        "Chrysler" to listOf("קרייזלר"),
        "Dodge" to listOf("דודג'"),
        "Genesis" to listOf("ג'נסיס"),
        "Lynk & Co" to listOf("לינק אנד קו"),
        "Smart" to listOf("סמארט"),
        "Jaguar" to listOf("יגואר"),
        "Daihatsu" to listOf("דייהטסו"),
        "SsangYong" to listOf("סאנגיונג"),
        "Polestar" to listOf("פולסטאר"),
        "Zeekr" to listOf("זיקר"),
        "Xpeng" to listOf("אקספנג"),
        "Leapmotor" to listOf("ליפמוטור"),
    )

    /** Feminine and plural forms too: Hebrew speakers say both "אדום" and "אדומה" for a car. */
    private val colors: Map<String, List<String>> = linkedMapOf(
        "White" to listOf("לבן", "לבנה"),
        "Black" to listOf("שחור", "שחורה"),
        "Silver" to listOf("כסוף", "כסופה", "כסף"),
        "Gray" to listOf("אפור", "אפורה"),
        "Red" to listOf("אדום", "אדומה"),
        "Blue" to listOf("כחול", "כחולה"),
        "Light Blue" to listOf("תכלת"),
        "Green" to listOf("ירוק", "ירוקה"),
        "Yellow" to listOf("צהוב", "צהובה"),
        "Orange" to listOf("כתום", "כתומה"),
        "Brown" to listOf("חום", "חומה"),
        "Beige" to listOf("בז'"),
        "Gold" to listOf("זהב", "זהוב", "זהובה"),
        "Purple" to listOf("סגול", "סגולה"),
        "Bordeaux" to listOf("בורדו"),
    )

    /** Accepted English spellings that differ from the canonical one. */
    private val englishAliases = mapOf(
        "grey" to "Gray",
        "mercedes benz" to "Mercedes",
        "mercedes-benz" to "Mercedes",
        "vw" to "Volkswagen",
        "citroën" to "Citroen",
        "maroon" to "Bordeaux",
        "burgundy" to "Bordeaux",
    )

    private fun table(kind: OptionKind) = if (kind == OptionKind.Color) colors else companies

    private val toEnglishIndex: Map<OptionKind, Map<String, String>> = OptionKind.entries.associateWith { kind ->
        buildMap {
            table(kind).forEach { (english, hebrew) ->
                put(key(english), english)
                hebrew.forEach { put(key(it), english) }
            }
            englishAliases.forEach { (alias, english) -> if (english in table(kind)) put(key(alias), english) }
        }
    }

    /**
     * The English name to store for what the user picked or typed: a known Hebrew or English
     * name becomes its canonical English spelling; anything else is kept as typed (trimmed).
     */
    fun toEnglish(kind: OptionKind, name: String): String {
        val trimmed = name.trim().replace(Regex("\\s+"), " ")
        return toEnglishIndex.getValue(kind)[key(trimmed)] ?: trimmed
    }

    /** The Hebrew display name for a stored value, or the value itself when there's no translation. */
    fun toHebrew(kind: OptionKind, stored: String): String =
        table(kind)[toEnglish(kind, stored)]?.first() ?: stored

    /**
     * Comparison key: case-insensitive and ignoring spaces, dots, dashes and the different
     * apostrophes/quotes people type in Hebrew abbreviations (ג'יפ / ג׳יפ, ב.מ.וו / במוו).
     */
    private fun key(name: String): String =
        name.lowercase().filterNot { it.isWhitespace() || it in IGNORED }

    private const val IGNORED = ".-־'\"`׳״’"
}
