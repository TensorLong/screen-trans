package com.galaxy.airviewdictionary.data.remote.geolocale

import android.content.Context
import com.galaxy.airviewdictionary.data.AVDRepository
import com.galaxy.airviewdictionary.data.remote.geolocale.GeoLocaleInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class GeoLocaleRepository @Inject constructor(@ApplicationContext val context: Context) : AVDRepository() {

    companion object {
        fun getCurrencyDetails(currencyCode: String): Pair<String, String> {
            val currencyMap = mapOf(
                "USD" to Pair("US Dollar", "$"),
                "EUR" to Pair("Euro", "€"),
                "GBP" to Pair("British Pound", "£"),
                "JPY" to Pair("Japanese Yen", "¥"),
                "AUD" to Pair("Australian Dollar", "A$"),
                "CAD" to Pair("Canadian Dollar", "C$"),
                "CHF" to Pair("Swiss Franc", "CHF"),
                "CNY" to Pair("Chinese Yuan", "¥"),
                "SEK" to Pair("Swedish Krona", "kr"),
                "NZD" to Pair("New Zealand Dollar", "NZ$"),
                "KRW" to Pair("South Korean Won", "₩"),
                "INR" to Pair("Indian Rupee", "₹"),
                "RUB" to Pair("Russian Ruble", "₽"),
                "BRL" to Pair("Brazilian Real", "R$"),
                "ZAR" to Pair("South African Rand", "R"),
                "SGD" to Pair("Singapore Dollar", "S$"),
                "HKD" to Pair("Hong Kong Dollar", "HK$"),
                "NOK" to Pair("Norwegian Krone", "kr"),
                "MXN" to Pair("Mexican Peso", "$"),
                "THB" to Pair("Thai Baht", "฿"),
                "TRY" to Pair("Turkish Lira", "₺"),
                "AED" to Pair("UAE Dirham", "د.إ"),
                "SAR" to Pair("Saudi Riyal", "﷼"),
                "MYR" to Pair("Malaysian Ringgit", "RM"),
                "IDR" to Pair("Indonesian Rupiah", "Rp"),
                "PHP" to Pair("Philippine Peso", "₱"),
                "CZK" to Pair("Czech Koruna", "Kč"),
                "PLN" to Pair("Polish Zloty", "zł"),
                "HUF" to Pair("Hungarian Forint", "Ft"),
                "DKK" to Pair("Danish Krone", "kr"),
                "ILS" to Pair("Israeli Shekel", "₪"),
                "EGP" to Pair("Egyptian Pound", "ج.م"),
                "PKR" to Pair("Pakistani Rupee", "₨"),
                "VND" to Pair("Vietnamese Dong", "₫"),
                "CLP" to Pair("Chilean Peso", "$"),
                "ARS" to Pair("Argentine Peso", "$"),
                "COP" to Pair("Colombian Peso", "$"),
                "BDT" to Pair("Bangladeshi Taka", "৳"),
                "KWD" to Pair("Kuwaiti Dinar", "د.ك")
            )

            // Return the matching value or a default value
            return currencyMap[currencyCode] ?: Pair("Unknown", "-")
        }
    }

    private val _geoLocaleInfoFlow = MutableStateFlow<GeoLocaleInfo?>(getLocaleInfo())

    val geoLocaleInfoFlow: StateFlow<GeoLocaleInfo?> get() = _geoLocaleInfoFlow

    private fun initGeoLocale(locale: Locale, attempts: Int = 0) {
        Timber.tag(TAG).i("=========================== initGeoLocale ==========================")

        launchInAVDCoroutineScope {
            val client = OkHttpClient()
            val url = "http://ip-api.com/json/?fields=status,message,country,countryCode,region,regionName,timezone,currency"
            val request = Request.Builder()
                .url(url)
                .build()

            var success = false

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        responseBody?.let {
                            // Parse the response using JSONObject
                            val json = JSONObject(it)
                            Timber.tag(TAG).d("Response JSON: $json")
                            val status = json.getString("status")
                            if (status == "success") {
                                val currencyCode = json.optString("currency", "Unknown")
                                val (currencyName, currencySymbol) = getCurrencyDetails(currencyCode)

                                GeoLocaleInfo(
                                    country = json.optString("country", "Unknown"),
                                    countryCode = json.optString("countryCode", "Unknown"),
                                    currencyName = currencyName,
                                    currencyCode = currencyCode,
                                    currencySymbol = currencySymbol,
                                ).also { geoLocaleInfo ->
                                    Timber.tag(TAG).d("GeoLocaleInfo: $geoLocaleInfo")
                                    _geoLocaleInfoFlow.value = geoLocaleInfo
                                }
                                success = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Request failed")
            }

            if (!success && attempts < 10) {
                delay((2000 + 1000 * attempts).toLong()) // Wait XX seconds before retrying
                Timber.tag(TAG).w("initGeoLocale failed, retrying... Attempt: ${attempts + 1}")
                initGeoLocale(locale, attempts + 1)
            }
        }
    }

    private fun getLocaleInfo(): GeoLocaleInfo? {
        return try {
            val locale = Locale.getDefault() // 기기의 기본 로케일
            val currencyCode = Currency.getInstance(locale).currencyCode // 통화 코드
            val (currencyName, currencySymbol) = getCurrencyDetails(currencyCode)

            return GeoLocaleInfo(
                country = locale.displayCountry, // 국가 이름
                countryCode = locale.country,// ISO 3166-1 alpha-2 국가 코드
                currencyName = currencyName,
                currencyCode = currencyCode,
                currencySymbol = currencySymbol
            ).also { geoLocaleInfo ->
                Timber.tag(TAG).d("device GeoLocaleInfo: $geoLocaleInfo")
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getCurrentLocale(): Locale {
        return context.resources.configuration.locales.get(0)
    }

    private fun isValidLocale(testLocale: Locale): Boolean {
        return Locale.getAvailableLocales().any {
            it.language.equals(testLocale.language, ignoreCase = true) &&
                    it.country.equals(testLocale.country, ignoreCase = true) &&
                    it.variant.equals(testLocale.variant, ignoreCase = true)
        }
    }


    init {
        initGeoLocale(getCurrentLocale())
    }

    override fun onZeroReferences() {

    }
}










