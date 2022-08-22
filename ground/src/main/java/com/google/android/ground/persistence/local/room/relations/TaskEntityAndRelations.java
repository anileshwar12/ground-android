/*
 * Copyright 2019 Google LLC
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

package com.google.android.ground.persistence.local.room.relations;

import androidx.room.Embedded;
import androidx.room.Relation;
import com.google.android.ground.persistence.local.room.entity.MultipleChoiceEntity;
import com.google.android.ground.persistence.local.room.entity.OptionEntity;
import com.google.android.ground.persistence.local.room.entity.TaskEntity;
import java.util.List;

/**
 * Represents relationship between TaskEntity, MultipleChoiceEntity, and OptionEntity.
 *
 * <p>Querying any of the below data classes automatically loads the task annotated as @Relation.
 */
public class TaskEntityAndRelations {
  @Embedded public TaskEntity taskEntity;

  @Relation(parentColumn = "id", entityColumn = "task_id", entity = MultipleChoiceEntity.class)
  public List<MultipleChoiceEntity> multipleChoiceEntities;

  @Relation(parentColumn = "id", entityColumn = "task_id", entity = OptionEntity.class)
  public List<OptionEntity> optionEntities;
}