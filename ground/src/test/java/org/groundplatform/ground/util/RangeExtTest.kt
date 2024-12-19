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

package org.groundplatform.ground.util

import org.groundplatform.android.util.rangeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class RangeExtTest {
  @Test
  fun `rangeOf() returns range of multiple elements`() {
    assertEquals(-10..400, listOf(6, 2, 8, 1, -10, 400).rangeOf { it })
  }

  @Test
  fun `rangeOf() returns range on single element`() {
    assertEquals(42..42, listOf(42).rangeOf { it })
  }

  @Test
  fun `rangeOf() returns EMPTY on empty list`() {
    assertEquals(IntRange.EMPTY, emptyList<Int>().rangeOf { it })
  }
}