package com.galaxy.airviewdictionary.data.local.vision.model

import android.graphics.Point
import android.graphics.Rect
import com.galaxy.airviewdictionary.extensions.bottomLeft
import com.galaxy.airviewdictionary.extensions.bottomRight
import com.galaxy.airviewdictionary.extensions.topLeft
import com.galaxy.airviewdictionary.extensions.topRight


/**
 * [VisionText] 가 형성하는 다각형을 나타내기 위한 데이터 클래스.
 */
data class Polygon(val points: List<Point>) {

    // Point가 Polygon 안에 있는지 확인하는 함수
    fun contains(point: Point): Boolean {
        var intersects = 0
        val n = points.size
        for (i in 0 until n) {
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            if (isIntersecting(point, p1, p2)) {
                intersects++
            }
        }
        return intersects % 2 != 0
    }

    private fun isIntersecting(point: Point, p1: Point, p2: Point): Boolean {
        if (p1.y > p2.y) {
            return isIntersecting(point, p2, p1)
        }
        if (point.y == p1.y || point.y == p2.y) {
            point.y += 1
        }
        if (point.y < p1.y || point.y > p2.y) {
            return false
        }
        if (p1.x == p2.x) {
            return point.x <= p1.x
        }
        val xIntersection = (point.y - p1.y).toFloat() / (p2.y - p1.y) * (p2.x - p1.x) + p1.x
        return point.x <= xIntersection
    }

    companion object {
        fun fromRects(rects: List<Rect>): Polygon {
            if (rects.isEmpty()) throw IllegalArgumentException("The list of rectangles must not be empty")

            val points = mutableListOf<Point>()

            // 모든 Rect의 꼭짓점을 추출
            rects.forEach { rect ->
                points.add(rect.topLeft)
                points.add(rect.topRight)
                points.add(rect.bottomLeft)
                points.add(rect.bottomRight)
            }

            // 꼭짓점을 x좌표, y좌표 순으로 정렬
            points.sortWith(compareBy<Point> { it.x }.thenBy { it.y })

            val convexHull = mutableListOf<Point>()

            // Convex Hull 알고리즘 (Graham Scan 사용)
            // 좌측 하단부터 우측 상단까지 시계 방향으로 탐색

            // 왼쪽 아래부터 오른쪽 위까지의 경로
            for (point in points) {
                while (convexHull.size >= 2 && ccw(convexHull[convexHull.size - 2], convexHull.last(), point) <= 0) {
                    convexHull.removeAt(convexHull.size - 1)
                }
                convexHull.add(point)
            }

            // 오른쪽 위부터 왼쪽 아래까지의 경로
            val size = convexHull.size + 1
            for (i in points.size - 2 downTo 0) {
                val point = points[i]
                while (convexHull.size >= size && ccw(convexHull[convexHull.size - 2], convexHull.last(), point) <= 0) {
                    convexHull.removeAt(convexHull.size - 1)
                }
                convexHull.add(point)
            }

            convexHull.removeAt(convexHull.size - 1)

            return Polygon(convexHull)
        }

        private fun ccw(p1: Point, p2: Point, p3: Point): Int {
            return (p2.x - p1.x) * (p3.y - p1.y) - (p2.y - p1.y) * (p3.x - p1.x)
        }
    }
}
