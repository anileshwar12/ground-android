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
package org.groundplatform.android.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.ground.model.submission.DraftSubmission
import com.google.android.ground.persistence.sync.MediaUploadWorkManager
import com.google.android.ground.persistence.sync.MutationSyncWorkManager
import com.google.android.ground.repository.MutationRepository
import com.google.android.ground.repository.OfflineAreaRepository
import com.google.android.ground.repository.SubmissionRepository
import com.google.android.ground.repository.SurveyRepository
import com.google.android.ground.repository.UserRepository
import com.google.android.ground.ui.common.AbstractViewModel
import com.google.android.ground.ui.common.SharedViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

@SharedViewModel
class HomeScreenViewModel
@Inject
internal constructor(
  private val offlineAreaRepository: OfflineAreaRepository,
  private val submissionRepository: SubmissionRepository,
  private val mutationRepository: MutationRepository,
  private val mutationSyncWorkManager: MutationSyncWorkManager,
  private val mediaUploadWorkManager: MediaUploadWorkManager,
  val surveyRepository: SurveyRepository,
  val userRepository: UserRepository,
) : AbstractViewModel() {

  private val _openDrawerRequests: MutableSharedFlow<Unit> = MutableSharedFlow()
  val openDrawerRequestsFlow: SharedFlow<Unit> = _openDrawerRequests.asSharedFlow()

  // TODO(#1730): Allow tile source configuration from a non-survey accessible source.
  val showOfflineAreaMenuItem: LiveData<Boolean> = MutableLiveData(true)

  init {
    viewModelScope.launch { kickLocalMutationSyncWorkers() }
  }

  /**
   * Enqueue data and photo upload workers for all pending mutations when home screen is first
   * opened as a workaround the get stuck mutations (i.e., PENDING or FAILED mutations with no
   * scheduled workers) going again. If there are no mutations in the upload queue this will be a
   * no-op. Workaround for https://github.com/groundplatform/ground-android/issues/2751.
   */
  private suspend fun kickLocalMutationSyncWorkers() {
    if (mutationRepository.getIncompleteUploads().isNotEmpty()) {
      mutationSyncWorkManager.enqueueSyncWorker()
    }
    if (mutationRepository.getIncompleteMediaMutations().isNotEmpty()) {
      mediaUploadWorkManager.enqueueSyncWorker()
    }
  }

  /** Attempts to return draft submission for the currently active survey. */
  suspend fun getDraftSubmission(): DraftSubmission? {
    val draftId = submissionRepository.getDraftSubmissionsId()
    val survey = surveyRepository.activeSurvey

    if (draftId.isEmpty() || survey == null) {
      // Missing draft submission
      return null
    }

    val draft = submissionRepository.getDraftSubmission(draftId, survey) ?: return null

    if (draft.surveyId != survey.id) {
      Timber.e("Skipping draft submission, survey id doesn't match")
      return null
    }

    // TODO: Check whether the previous user id matches with current user or not.
    return draft
  }

  fun openNavDrawer() {
    viewModelScope.launch { _openDrawerRequests.emit(Unit) }
  }

  suspend fun getOfflineAreas() = offlineAreaRepository.offlineAreas().first()

  fun signOut() {
    viewModelScope.launch { userRepository.signOut() }
  }
}