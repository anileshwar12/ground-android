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
package com.google.android.ground.ui.datacollection

import android.graphics.Color.parseColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.ground.databinding.DropAPinTaskBinding
import com.google.android.ground.model.job.Style
import com.google.android.ground.model.task.Task
import com.google.android.ground.ui.MarkerIconFactory
import com.google.android.ground.ui.common.AbstractMapContainerFragment
import com.google.android.ground.ui.common.BaseMapViewModel
import com.google.android.ground.ui.map.CameraPosition
import com.google.android.ground.ui.map.MapFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DropAPinTaskFragment(task: Task, private val viewModel: DropAPinTaskViewModel) :
  AbstractMapContainerFragment() {

  @Inject lateinit var markerIconFactory: MarkerIconFactory

  private lateinit var mapViewModel: BaseMapViewModel
  private lateinit var binding: DropAPinTaskBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    mapViewModel = getViewModel(BaseMapViewModel::class.java)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = DropAPinTaskBinding.inflate(inflater, container, false)
    binding.viewModel = mapViewModel
    binding.lifecycleOwner = this
    return binding.root
  }

  override fun onMapReady(mapFragment: MapFragment) {}

  override fun getMapViewModel(): BaseMapViewModel = mapViewModel

  override fun onMapCameraMoved(position: CameraPosition) {
    super.onMapCameraMoved(position)
    viewModel.updateResponse(position)
    refreshMarker()
  }

  private fun refreshMarker() {
    val markerBitmap =
      markerIconFactory.getMarkerBitmap(
        parseColor(Style().color),
        mapFragment.currentZoomLevel,
        false
      )
    binding.markerIcon.setImageBitmap(markerBitmap)
  }
}