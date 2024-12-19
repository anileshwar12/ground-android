/*
 * Copyright 2020 Google LLC
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

package org.groundplatform.android.persistence.remote.firebase.schema

import org.groundplatform.android.model.Survey as SurveyModel
import org.groundplatform.android.model.job.Job
import org.groundplatform.android.persistence.remote.DataStoreException
import org.groundplatform.android.persistence.remote.firebase.protobuf.parseFrom
import org.groundplatform.android.proto.Survey as SurveyProto
import org.groundplatform.android.proto.Survey
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.collections.immutable.toPersistentMap

/** Converts between Firestore documents and [SurveyModel] instances. */
internal object SurveyConverter {

  @Throws(DataStoreException::class)
  fun toSurvey(doc: DocumentSnapshot, jobs: List<Job> = listOf()): SurveyModel {
    if (!doc.exists()) throw DataStoreException("Missing survey")

    val surveyFromProto = SurveyProto::class.parseFrom(doc, 1)
    val jobMap = jobs.associateBy { it.id }
    val dataSharingTerms =
      if (surveyFromProto.dataSharingTerms.type == Survey.DataSharingTerms.Type.TYPE_UNSPECIFIED) {
        null
      } else {
        surveyFromProto.dataSharingTerms
      }
    return SurveyModel(
      surveyFromProto.id.ifEmpty { doc.id },
      surveyFromProto.name,
      surveyFromProto.description,
      jobMap.toPersistentMap(),
      surveyFromProto.aclMap.entries.associate { it.key to it.value.toString() },
      dataSharingTerms = dataSharingTerms,
    )
  }
}
