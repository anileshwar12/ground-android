/*
 * Copyright 2023 Google LLC
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
package org.groundplatform.android.persistence.sync

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import org.groundplatform.android.BaseHiltTest
import org.groundplatform.android.model.mutation.Mutation
import org.groundplatform.android.model.mutation.Mutation.SyncStatus.*
import org.groundplatform.android.model.mutation.SubmissionMutation
import org.groundplatform.android.model.submission.PhotoTaskData
import org.groundplatform.android.model.submission.ValueDelta
import org.groundplatform.android.model.task.Task
import org.groundplatform.android.model.task.Task.Type.PHOTO
import org.groundplatform.android.persistence.local.stores.LocalLocationOfInterestStore
import org.groundplatform.android.persistence.local.stores.LocalSubmissionStore
import org.groundplatform.android.persistence.local.stores.LocalSurveyStore
import org.groundplatform.android.persistence.local.stores.LocalUserStore
import org.groundplatform.android.repository.MutationRepository
import org.groundplatform.android.repository.UserMediaRepository
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sharedtest.FakeData
import com.sharedtest.persistence.remote.FakeRemoteDataStore
import com.sharedtest.persistence.remote.FakeRemoteStorageManager
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.robolectric.RobolectricTestRunner

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class MediaUploadWorkerTest : BaseHiltTest() {
  private lateinit var context: Context
  @Inject lateinit var fakeRemoteDataStore: FakeRemoteDataStore
  @Inject lateinit var mutationRepository: MutationRepository
  @Inject lateinit var userMediaRepository: UserMediaRepository
  @Inject lateinit var fakeRemoteStorageManager: FakeRemoteStorageManager
  @Inject lateinit var localSubmissionStore: LocalSubmissionStore
  @Inject lateinit var localUserStore: LocalUserStore
  @Inject lateinit var localSurveyStore: LocalSurveyStore
  @Inject lateinit var localLocationOfInterestStore: LocalLocationOfInterestStore
  @BindValue @Mock lateinit var mockFirebaseCrashlytics: FirebaseCrashlytics

  private val factory =
    object : WorkerFactory() {
      override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
      ): ListenableWorker =
        MediaUploadWorker(
          context,
          workerParameters,
          fakeRemoteStorageManager,
          mutationRepository,
          userMediaRepository,
        )
    }

  @Before
  override fun setUp() {
    super.setUp()
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun doWork_succeedsOnExistingPhoto() = runWithTestDispatcher {
    localUserStore.insertOrUpdateUser(FakeData.USER)
    localSurveyStore.insertOrUpdateSurvey(org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.TEST_SURVEY)
    localLocationOfInterestStore.insertOrUpdate(org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.TEST_LOI)
    localSubmissionStore.applyAndEnqueue(
      createSubmissionMutation().copy(syncStatus = MEDIA_UPLOAD_PENDING)
    )
    createAndDoWork(context)
    assertThatMutationCountEquals(COMPLETED, 1)
  }

  @Test
  fun doWork_failsOnNonExistentPhotos() = runWithTestDispatcher {
    localUserStore.insertOrUpdateUser(FakeData.USER)
    localSurveyStore.insertOrUpdateSurvey(org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.TEST_SURVEY)
    localLocationOfInterestStore.insertOrUpdate(org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.TEST_LOI)
    localSubmissionStore.applyAndEnqueue(
      createSubmissionMutation("does_not_exist.jpg").copy(syncStatus = MEDIA_UPLOAD_PENDING)
    )
    createAndDoWork(context)
    assertThatMutationCountEquals(MEDIA_UPLOAD_AWAITING_RETRY, 1)
  }

  @Test
  fun doWork_propagatesFailures() = runWithTestDispatcher {
    val mutation = createSubmissionMutation() // a valid mutation
    val delta = buildList {
      addAll(mutation.deltas)
      add(ValueDelta(org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.PHOTO_TASK_ID, PHOTO, PhotoTaskData("some/path/does_not_exist.jpg")))
    }
    val updatedMutation =
      mutation.copy(
        deltas = delta,
        syncStatus = MEDIA_UPLOAD_PENDING,
      ) // add an additional non-existent photo to the mutation

    localUserStore.insertOrUpdateUser(FakeData.USER)
    localSurveyStore.insertOrUpdateSurvey(org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.TEST_SURVEY)
    localLocationOfInterestStore.insertOrUpdate(org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.TEST_LOI)
    localSubmissionStore.applyAndEnqueue(updatedMutation)

    createAndDoWork(context)

    assertThatMutationCountEquals(MEDIA_UPLOAD_AWAITING_RETRY, 1)
    assertThatMutationCountEquals(MEDIA_UPLOAD_PENDING, 0)
    assertThatMutationCountEquals(MEDIA_UPLOAD_IN_PROGRESS, 0)
    assertThatMutationCountEquals(COMPLETED, 0)
  }

  @Test
  fun doWork_ignoresNonMediaMutations() = runWithTestDispatcher {
    localUserStore.insertOrUpdateUser(FakeData.USER)
    localSurveyStore.insertOrUpdateSurvey(org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.TEST_SURVEY)
    localLocationOfInterestStore.insertOrUpdate(org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.TEST_LOI)
    addSubmissionMutationToLocalStorage(PENDING)
    addSubmissionMutationToLocalStorage(FAILED)
    addSubmissionMutationToLocalStorage(IN_PROGRESS)
    addSubmissionMutationToLocalStorage(COMPLETED)
    addSubmissionMutationToLocalStorage(UNKNOWN)

    createAndDoWork(context)

    assertThatMutationCountEquals(PENDING, 1)
    assertThatMutationCountEquals(FAILED, 1)
    assertThatMutationCountEquals(IN_PROGRESS, 1)
    assertThatMutationCountEquals(COMPLETED, 1)
    assertThatMutationCountEquals(UNKNOWN, 1)
    assertThatMutationCountEquals(MEDIA_UPLOAD_AWAITING_RETRY, 0)
    assertThatMutationCountEquals(MEDIA_UPLOAD_PENDING, 0)
    assertThatMutationCountEquals(MEDIA_UPLOAD_IN_PROGRESS, 0)
  }

  // Initiates and runs the MediaUploadWorker
  private suspend fun createAndDoWork(context: Context) {
    TestListenableWorkerBuilder<MediaUploadWorker>(context)
      .setWorkerFactory(factory)
      .build()
      .doWork()
  }

  private suspend fun getMutations(syncStatus: Mutation.SyncStatus): List<Mutation> =
    (localLocationOfInterestStore.getAllMutationsFlow().first() +
        localSubmissionStore.getAllMutationsFlow().first())
      .filter { it.syncStatus == syncStatus }

  /**
   * Asserts that the specified number of mutations with the specified status exist in the local db.
   */
  private suspend fun assertThatMutationCountEquals(status: Mutation.SyncStatus, count: Int) {
    assertWithMessage("Expect $count mutations with status $status")
      .that(getMutations(status))
      .hasSize(count)
  }

  private suspend fun addSubmissionMutationToLocalStorage(status: Mutation.SyncStatus) =
    localSubmissionStore.applyAndEnqueue(createSubmissionMutation().copy(syncStatus = status))

  private suspend fun createSubmissionMutation(photoName: String? = null): SubmissionMutation {
    val photo =
      userMediaRepository.savePhoto(
        Bitmap.createBitmap(1, 1, Bitmap.Config.HARDWARE),
        org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.TEST_PHOTO_TASK.id,
      )

    return org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.SUBMISSION_MUTATION.copy(
      job = org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.TEST_JOB,
      deltas = listOf(ValueDelta(org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.PHOTO_TASK_ID, PHOTO, PhotoTaskData(photoName ?: photo.name))),
    )
  }

  // TODO: Replace all this w/ FakeData functions that return good base model objects.
  companion object {
    private const val PHOTO_TASK_ID = "photo_task_id"
    private val TEST_PHOTO_TASK: Task =
      Task(id = "photo_task_id", index = 1, isRequired = true, type = PHOTO, label = "photo_task")

    private val TEST_JOB = FakeData.JOB.copy(tasks = mapOf(org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.PHOTO_TASK_ID to org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.TEST_PHOTO_TASK))

    private val TEST_LOI = FakeData.LOCATION_OF_INTEREST.copy(job = org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.TEST_JOB)
    private val TEST_SURVEY = FakeData.SURVEY.copy(jobMap = mapOf(org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.TEST_JOB.id to org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.TEST_JOB))

    val SUBMISSION_MUTATION =
      SubmissionMutation(
        type = Mutation.Type.CREATE,
        syncStatus = PENDING,
        locationOfInterestId = FakeData.LOCATION_OF_INTEREST.id,
        userId = FakeData.USER.id,
        job = FakeData.JOB,
        surveyId = FakeData.SURVEY.id,
        collectionId = "collectionId",
        deltas = listOf(ValueDelta(org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.PHOTO_TASK_ID, PHOTO, PhotoTaskData("foo/${org.groundplatform.android.persistence.sync.MediaUploadWorkerTest.Companion.PHOTO_TASK_ID}.jpg"))),
      )
  }
}