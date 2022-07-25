/*
 * Copyright 2022 Google LLC
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

package com.google.android.ground.persistence.remote.firestore

import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon

typealias Path = Array<Pair<Double, Double>>

class GeometryConverterTest {
    private val x = -42.121
    private val y = 28.482
    private val path1 = arrayOf(
        -89.63410225 to 41.89729784,
        -89.63805046 to 41.89525340,
        -89.63659134 to 41.88937530,
        -89.62886658 to 41.88956698,
        -89.62800827 to 41.89544507,
        -89.63410225 to 41.89729784
    )
    private val path2 = arrayOf(
        -89.63453141 to 41.89193106,
        -89.63118400 to 41.89090878,
        -89.63066902 to 41.89397560,
        -89.63358726 to 41.89480618,
        -89.63453141 to 41.89193106
    )
    private val path3 = arrayOf(
        -89.61006966 to 41.89333669,
        -89.61479034 to 41.89832003,
        -89.61719360 to 41.89455062,
        -89.61521950 to 41.89154771,
        -89.61006966 to 41.89333669
    )
    private val path4 = arrayOf(
        -89.61393204 to 41.89320891,
        -89.61290207 to 41.89429505,
        -89.61418953 to 41.89538118,
        -89.61513367 to 41.89416727,
        -89.61393204 to 41.89320891
    )

    private val converter = GeometryConverter()
    private val geometryFactory = GeometryFactory()

    @Test
    fun toFirestoreMap_point() {
        assertEquals(
            mapOf(
                "type" to "Point",
                "coordinates" to GeoPoint(x, y)
            ),
            converter.toFirestoreMap(
                point(x, y)
            )
        )
    }

    @Test
    fun toFirestoreMap_multiPolygon() {
        assertEquals(
            mapOf(
                "type" to "MultiPolygon",
                "coordinates" to mapOf(
                    0 to mapOf(
                        0 to indexedGeoPointMap(path1),
                        1 to indexedGeoPointMap(path2)
                    ),
                    1 to mapOf(
                        0 to indexedGeoPointMap(path3),
                        1 to indexedGeoPointMap(path4)
                    )
                )
            ),
            converter.toFirestoreMap(
                multiPolygon(
                    polygon(path1, path2),
                    polygon(path3, path4)
                )
            )
        )
    }

    @Test
    fun fromFirestoreMap_point() {
        assertEquals(
            point(x, y),
            converter.fromFirestoreMap(
                mapOf(
                    "type" to "Point",
                    "coordinates" to GeoPoint(x, y)
                )
            )
        )
    }

    @Test
    fun fromFirestoreMap_multiPolygon() {
        assertEquals(
            multiPolygon(polygon(path1, path2), polygon(path3, path4)),
            converter.fromFirestoreMap(
                mapOf(
                    "type" to "MultiPolygon",
                    "coordinates" to mapOf(
                        0 to mapOf(
                            0 to indexedGeoPointMap(path1),
                            1 to indexedGeoPointMap(path2)
                        ),
                        1 to mapOf(
                            0 to indexedGeoPointMap(path3),
                            1 to indexedGeoPointMap(path4)
                        )
                    )
                )
            )
        )
    }

    private fun point(x: Double, y: Double): Point =
        geometryFactory.createPoint(Coordinate(x, y))

    private fun linearRing(path: Path) =
        geometryFactory.createLinearRing(coordinateArray(path))

    private fun polygon(shell: Path, vararg holes: Path): Polygon =
        geometryFactory.createPolygon(
            linearRing(shell),
            holes.map(::linearRing).toTypedArray()
        )

    private fun multiPolygon(vararg polygons: Polygon) =
        geometryFactory.createMultiPolygon(polygons)

    private fun coordinateArray(path: Path): Array<Coordinate> =
        path.map { Coordinate(it.first, it.second) }.toTypedArray()

    private fun indexedGeoPointMap(path: Path): Map<Int, Any> =
        path.mapIndexed { idx, it -> idx to GeoPoint(it.first, it.second) }.toMap()
}