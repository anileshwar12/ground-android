/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.android.ui.home.mapcontainer

import androidx.lifecycle.viewModelScope
import com.google.android.ground.Config.CLUSTERING_ZOOM_THRESHOLD
import com.google.android.ground.model.Survey
import com.google.android.ground.model.job.Job
import com.google.android.ground.model.job.getDefaultColor
import com.google.android.ground.model.locationofinterest.LocationOfInterest
import com.google.android.ground.persistence.local.LocalValueStore
import com.google.android.ground.proto.Survey.DataSharingTerms
import com.google.android.ground.repository.LocationOfInterestRepository
import com.google.android.ground.repository.MapStateRepository
import com.google.android.ground.repository.OfflineAreaRepository
import com.google.android.ground.repository.SubmissionRepository
import com.google.android.ground.repository.SurveyRepository
import com.google.android.ground.system.LocationManager
import com.google.android.ground.system.PermissionsManager
import com.google.android.ground.system.SettingsManager
import com.google.android.ground.ui.common.BaseMapViewModel
import com.google.android.ground.ui.common.SharedViewModel
import com.google.android.ground.ui.home.mapcontainer.cards.MapCardUiData
import com.google.android.ground.ui.map.Feature
import com.google.android.ground.ui.map.FeatureType
import com.google.android.ground.ui.map.isLocationOfInterest
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@SharedViewModel
class HomeScreenMapContainerViewModel
@Inject
internal constructor(
  private val loiRepository: LocationOfInterestRepository,
  mapStateRepository: MapStateRepository,
  private val submissionRepository: SubmissionRepository,
  locationManager: LocationManager,
  settingsManager: SettingsManager,
  offlineAreaRepository: OfflineAreaRepository,
  permissionsManager: PermissionsManager,
  surveyRepository: SurveyRepository,
  private val localValueStore: LocalValueStore,
) :
  BaseMapViewModel(
    locationManager,
    mapStateRepository,
    settingsManager,
    offlineAreaRepository,
    permissionsManager,
    surveyRepository,
    loiRepository,
  ) {

  private val selectedLoiIdFlow = MutableStateFlow<String?>(null)

  private val activeSurvey: StateFlow<Survey?> = surveyRepository.activeSurveyFlow

  /** Captures essential, high-level derived properties for a given survey. */
  data class SurveyProperties(val addLoiPermitted: Boolean, val noLois: Boolean)

  /**
   * This flow emits [SurveyProperties] when the active survey changes. Callers can use this data to
   * determine if and how behavior should change based on differing survey properties.
   */
  val surveyUpdateFlow: Flow<SurveyProperties> =
    activeSurvey.filterNotNull().map { survey ->
      val lois = loiRepository.getValidLois(survey).first()
      val addLoiPermitted = survey.jobs.any { job -> job.canDataCollectorsAddLois }
      SurveyProperties(addLoiPermitted = addLoiPermitted, noLois = lois.isEmpty())
    }

  /** Set of [Feature] to render on the map. */
  val mapLoiFeatures: Flow<Set<Feature>>

  /**
   * List of [LocationOfInterest] for the active survey that are present within the viewport and
   * zoom level is clustering threshold or higher.
   */
  private val loisInViewport: StateFlow<List<LocationOfInterest>>

  /** [LocationOfInterest] clicked by the user. */
  val loiClicks: MutableStateFlow<LocationOfInterest?> = MutableStateFlow(null)

  /**
   * List of [Job]s which allow LOIs to be added during field collection, populated only when zoomed
   * in far enough.
   */
  private val adHocLoiJobs: Flow<List<Job>>

  /** Emits whether the current zoom has crossed the zoomed-in threshold or not to cluster LOIs. */
  val isZoomedInFlow: Flow<Boolean>

  /** Emits the data sharing terms object when the active survey has changed. Null to show none. */
  val activeSurveyDataSharingTermsFlow: Flow<DataSharingTerms?>

  init {
    // THIS SHOULD NOT BE CALLED ON CONFIG CHANGE
    // TODO: Clear location of interest markers when survey is deactivated.
    // TODO: Since we depend on survey stream from repo anyway, this transformation can be moved
    //  into the repository.

    @OptIn(FlowPreview::class)
    mapLoiFeatures =
      activeSurvey.flatMapLatest {
        if (it == null) flowOf(setOf())
        else
          getLocationOfInterestFeatures(it)
            .debounce(1000.milliseconds)
            .distinctUntilChanged()
            .combine(selectedLoiIdFlow) { loiFeatures, selectedLoiId ->
              updatedLoiSelectedStates(loiFeatures, selectedLoiId)
            }
      }

    isZoomedInFlow =
      getCurrentCameraPosition().mapNotNull { it.zoomLevel }.map { it >= CLUSTERING_ZOOM_THRESHOLD }

    loisInViewport =
      activeSurvey
        .combine(isZoomedInFlow) { survey, isZoomedIn -> Pair(survey, isZoomedIn) }
        .flatMapLatest { (survey, isZoomedIn) ->
          val bounds = currentCameraPosition.value?.bounds
          if (bounds == null || survey == null || !isZoomedIn) flowOf(listOf())
          else loiRepository.getWithinBounds(survey, bounds)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, listOf())

    adHocLoiJobs =
      activeSurvey.combine(isZoomedInFlow) { survey, isZoomedIn ->
        if (survey == null || !isZoomedIn) listOf()
        else survey.jobs.filter { it.canDataCollectorsAddLois && it.getAddLoiTask() != null }
      }

    activeSurveyDataSharingTermsFlow =
      activeSurvey.flatMapLatest { survey ->
        flowOf(
          survey?.let {
            it.dataSharingTerms?.let { dataTerms ->
              if (getDataSharingConsent(it)) {
                // User previously agreed to the terms.
                null
              } else {
                dataTerms
              }
            }
          }
        )
      }
  }

  /**
   * Returns a flow of [MapCardUiData] associated with the active survey's LOIs and adhoc jobs for
   * displaying the cards.
   */
  fun getMapCardUiData(): Flow<Pair<List<MapCardUiData>, Int>> =
    loisInViewport.combine(adHocLoiJobs) { lois, jobs ->
      val loiCards = lois.map { MapCardUiData.LoiCardUiData(it) }
      val jobCards = jobs.map { MapCardUiData.AddLoiCardUiData(it) }

      Pair(loiCards + jobCards, lois.size)
    }

  private fun updatedLoiSelectedStates(
    features: Set<Feature>,
    selectedLoiId: String?,
  ): Set<Feature> =
    features
      .map { it.withSelected(it.isLocationOfInterest() && it.tag.id == selectedLoiId) }
      .toSet()

  /**
   * Intended as a callback for when a specific map [Feature] is clicked. If the click is ambiguous,
   * (list of features > 1), it chooses the [Feature] with the smallest area. If multiple features
   * have the same area, or in the case of points, no area, the first is chosen. Does nothing if the
   * list of provided features is empty.
   */
  fun onFeatureClicked(features: Set<Feature>) {
    val geometry = features.map { it.geometry }.minByOrNull { it.area } ?: return
    for (loi in loisInViewport.value) {
      if (loi.geometry == geometry) {
        loiClicks.value = loi
      }
    }
  }

  suspend fun updateDataSharingConsent(dataSharingTerms: Boolean) {
    activeSurvey.collectLatest {
      if (it != null) {
        setDataSharingConsent(it, dataSharingTerms)
      }
    }
  }

  private fun getDataSharingConsent(survey: Survey) =
    localValueStore.getDataSharingConsent(survey.id)

  private fun setDataSharingConsent(survey: Survey, dataSharingTerms: Boolean) =
    localValueStore.setDataSharingConsent(survey.id, dataSharingTerms)

  private fun getLocationOfInterestFeatures(survey: Survey): Flow<Set<Feature>> =
    loiRepository.getValidLois(survey).map { it.map { loi -> loi.toFeature() }.toPersistentSet() }

  private suspend fun LocationOfInterest.toFeature() =
    Feature(
      id = id,
      type = FeatureType.LOCATION_OF_INTEREST.ordinal,
      flag = submissionRepository.getTotalSubmissionCount(this) > 0,
      geometry = geometry,
      style = Feature.Style(job.getDefaultColor()),
      clusterable = true,
      selected = true,
    )

  fun selectLocationOfInterest(id: String?) {
    selectedLoiIdFlow.value = id
  }
}