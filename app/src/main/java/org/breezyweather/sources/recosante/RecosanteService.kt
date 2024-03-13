/**
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.sources.recosante

import android.content.Context
import io.reactivex.rxjava3.core.Observable
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.wrappers.SecondaryWeatherWrapper
import org.breezyweather.R
import org.breezyweather.common.exceptions.InvalidLocationException
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.exceptions.SecondaryWeatherException
import org.breezyweather.common.source.LocationParametersSource
import org.breezyweather.common.source.PollenIndexSource
import org.breezyweather.common.source.SecondaryWeatherSource
import org.breezyweather.common.source.SecondaryWeatherSourceFeature
import retrofit2.Retrofit
import javax.inject.Inject

/**
 * Recosanté pollen service.
 */
class RecosanteService @Inject constructor(
    client: Retrofit.Builder
) : HttpSource(), SecondaryWeatherSource, PollenIndexSource, LocationParametersSource {

    override val id = "recosante"
    override val name = "Recosanté"
    override val privacyPolicyUrl = "https://recosante.beta.gouv.fr/donnees-personnelles/"

    private val mGeoApi by lazy {
        client
            .baseUrl(GEO_BASE_URL)
            .build()
            .create(GeoApi::class.java)
    }
    private val mPollenApi by lazy {
        client
            .baseUrl(RECOSANTE_BASE_URL)
            .build()
            .create(RecosanteApi::class.java)
    }

    override val supportedFeatures = listOf(SecondaryWeatherSourceFeature.FEATURE_POLLEN)
    override fun isFeatureSupportedForLocation(
        feature: SecondaryWeatherSourceFeature, location: Location
    ): Boolean {
        return !location.countryCode.isNullOrEmpty() && location.countryCode.equals("FR", ignoreCase = true)
    }
    override val airQualityAttribution = null
    override val pollenAttribution = "Recosanté, Le Réseau national de surveillance aérobiologique (RNSA) https://www.pollens.fr/"
    override val minutelyAttribution = null
    override val alertAttribution = null
    override val normalsAttribution = null

    override fun requestSecondaryWeather(
        context: Context, location: Location,
        requestedFeatures: List<SecondaryWeatherSourceFeature>
    ): Observable<SecondaryWeatherWrapper> {
        if (!isFeatureSupportedForLocation(SecondaryWeatherSourceFeature.FEATURE_POLLEN, location)) {
            // TODO: return Observable.error(UnsupportedFeatureForLocationException())
            return Observable.error(SecondaryWeatherException())
        }
        val insee = location.parameters
            .getOrElse(id) { null }?.getOrElse("insee") { null }
        if (insee.isNullOrEmpty()) {
            return Observable.error(SecondaryWeatherException())
        }

        return mPollenApi.getData(
            true,
            insee
        ).map {
            convert(location.timeZone, it)
        }
    }

    // Location parameters
    override fun needsLocationParametersRefresh(
        location: Location,
        coordinatesChanged: Boolean,
        features: List<SecondaryWeatherSourceFeature>
    ): Boolean {
        if (coordinatesChanged) return true

        val currentInsee = location.parameters
            .getOrElse(id) { null }?.getOrElse("insee") { null }

        return currentInsee.isNullOrEmpty()
    }

    override fun requestLocationParameters(
        context: Context, location: Location
    ): Observable<Map<String, String>> {
        return mGeoApi.getCommunes(location.longitude, location.latitude)
            .map { result ->
                if (result.isNotEmpty()) {
                    mapOf("insee" to result[0].code)
                } else {
                    throw InvalidLocationException()
                }
            }
    }

    override val pollenLabels = R.array.pollen_recosante_levels
    override val pollenColors = R.array.pollen_recosante_level_colors

    companion object {
        private const val GEO_BASE_URL = "https://geo.api.gouv.fr/"
        private const val RECOSANTE_BASE_URL = "https://api.recosante.beta.gouv.fr/"
    }
}