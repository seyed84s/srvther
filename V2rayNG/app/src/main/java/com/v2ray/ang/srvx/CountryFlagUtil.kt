package com.v2ray.ang.srvx

import java.util.Locale

/**
 * Utility to identify country flag emojis and localized Persian country names
 * from server remarks or hostnames.
 */
object CountryFlagUtil {

    data class CountryInfo(
        val code: String,
        val flag: String,
        val faName: String
    )

    private val countryMap = listOf(
        CountryInfo("DE", "🇩🇪", "آلمان"),
        CountryInfo("FI", "🇫🇮", "فنلاند"),
        CountryInfo("NL", "🇳🇱", "هلند"),
        CountryInfo("FR", "🇫🇷", "فرانسه"),
        CountryInfo("GB", "🇬🇧", "انگلستان"),
        CountryInfo("US", "🇺🇸", "آمریکا"),
        CountryInfo("TR", "🇹🇷", "ترکیه"),
        CountryInfo("CA", "🇨🇦", "کانادا"),
        CountryInfo("SG", "🇸🇬", "سنگاپور"),
        CountryInfo("AE", "🇦🇪", "امارات"),
        CountryInfo("PL", "🇵🇱", "لهستان"),
        CountryInfo("SE", "🇸🇪", "سوئد"),
        CountryInfo("CH", "🇨🇭", "سوئیس"),
        CountryInfo("IT", "🇮🇹", "ایتالیا"),
        CountryInfo("ES", "🇪🇸", "اسپانیا"),
        CountryInfo("JP", "🇯🇵", "ژاپن"),
        CountryInfo("RU", "🇷🇺", "روسیه"),
        CountryInfo("AT", "🇦🇹", "اتریش"),
        CountryInfo("NO", "🇳🇴", "نروژ"),
        CountryInfo("RO", "🇷🇴", "رومانی"),
        CountryInfo("UA", "🇺🇦", "اوکراین"),
        CountryInfo("IR", "🇮🇷", "ایران"),
        CountryInfo("GLOBAL", "🌐", "سرور اختصاصی")
    )

    /**
     * Resolves the country info based on server remarks or server host address.
     */
    fun getCountry(remarks: String?, serverAddress: String? = null): CountryInfo {
        val text = "${remarks.orEmpty()} ${serverAddress.orEmpty()}".lowercase(Locale.ROOT)

        return when {
            text.contains("germany") || text.contains("آلمان") || text.contains("frankfurt") || text.contains(" de ") || text.contains("-de") || text.contains(".de") -> countryMap[0]
            text.contains("finland") || text.contains("فنلاند") || text.contains("helsinki") || text.contains(" fi ") || text.contains("-fi") || text.contains(".fi") -> countryMap[1]
            text.contains("netherlands") || text.contains("هلند") || text.contains("amsterdam") || text.contains(" nl ") || text.contains("-nl") || text.contains(".nl") -> countryMap[2]
            text.contains("france") || text.contains("فرانسه") || text.contains("paris") || text.contains(" fr ") || text.contains("-fr") || text.contains(".fr") -> countryMap[3]
            text.contains("united kingdom") || text.contains("england") || text.contains("انگلیس") || text.contains("لندن") || text.contains("london") || text.contains(" gb ") || text.contains(" uk ") || text.contains("-uk") || text.contains(".uk") -> countryMap[4]
            text.contains("united states") || text.contains("usa") || text.contains("آمریکا") || text.contains("us") || text.contains("california") || text.contains("new york") || text.contains("-us") || text.contains(".us") -> countryMap[5]
            text.contains("turkey") || text.contains("ترکیه") || text.contains("istanbul") || text.contains(" tr ") || text.contains("-tr") || text.contains(".tr") -> countryMap[6]
            text.contains("canada") || text.contains("کانادا") || text.contains("toronto") || text.contains(" ca ") || text.contains("-ca") || text.contains(".ca") -> countryMap[7]
            text.contains("singapore") || text.contains("سنگاپور") || text.contains(" sg ") || text.contains("-sg") || text.contains(".sg") -> countryMap[8]
            text.contains("emirates") || text.contains("dubai") || text.contains("امارات") || text.contains("دبی") || text.contains(" ae ") || text.contains("-ae") || text.contains(".ae") -> countryMap[9]
            text.contains("poland") || text.contains("لهستان") || text.contains("warsaw") || text.contains(" pl ") || text.contains("-pl") || text.contains(".pl") -> countryMap[10]
            text.contains("sweden") || text.contains("سوئد") || text.contains("stockholm") || text.contains(" se ") || text.contains("-se") || text.contains(".se") -> countryMap[11]
            text.contains("switzerland") || text.contains("سوئیس") || text.contains("zurich") || text.contains(" ch ") || text.contains("-ch") || text.contains(".ch") -> countryMap[12]
            text.contains("italy") || text.contains("ایتالیا") || text.contains("milan") || text.contains("rome") || text.contains(" it ") || text.contains("-it") || text.contains(".it") -> countryMap[13]
            text.contains("spain") || text.contains("اسپانیا") || text.contains("madrid") || text.contains(" es ") || text.contains("-es") || text.contains(".es") -> countryMap[14]
            text.contains("japan") || text.contains("ژاپن") || text.contains("tokyo") || text.contains(" jp ") || text.contains("-jp") || text.contains(".jp") -> countryMap[15]
            text.contains("russia") || text.contains("روسیه") || text.contains("moscow") || text.contains(" ru ") || text.contains("-ru") || text.contains(".ru") -> countryMap[16]
            text.contains("iran") || text.contains("ایران") || text.contains("tehran") || text.contains(" ir ") || text.contains("-ir") || text.contains(".ir") -> countryMap[21]
            else -> countryMap[22] // Global default
        }
    }

    /**
     * Formats server remarks to include country flag emoji if not already present.
     */
    fun formatRemarksWithFlag(remarks: String, serverAddress: String? = null): String {
        // If remarks already contains an emoji flag, return as is
        if (remarks.contains("🇩🇪") || remarks.contains("🇫🇮") || remarks.contains("🇳🇱") ||
            remarks.contains("🇫🇷") || remarks.contains("🇬🇧") || remarks.contains("🇺🇸") ||
            remarks.contains("🇹🇷") || remarks.contains("🇨🇦") || remarks.contains("🇸🇬") ||
            remarks.contains("🇦🇪") || remarks.contains("🌐")) {
            return remarks
        }

        val country = getCountry(remarks, serverAddress)
        return "${country.flag} $remarks"
    }
}
