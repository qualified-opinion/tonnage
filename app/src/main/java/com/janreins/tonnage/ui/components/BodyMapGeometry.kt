package com.janreins.tonnage.ui.components

/** A point in one body figure, which is [BodyMapGeometry.WIDTH] by [BodyMapGeometry.HEIGHT]. */
data class BodyPoint(val x: Float, val y: Float)

/** One outlined region of a body figure. [muscle] is null for the plain body drawn underneath. */
data class BodyRegion(val muscle: String?, val points: List<BodyPoint>)

/** A cubic curve segment: two control points and the end point. */
data class BodyCurve(val control1: BodyPoint, val control2: BodyPoint, val end: BodyPoint)

/**
 * The shapes of the front and back body figures on the muscle map. Muscle names match the
 * exercise library's (see ExerciseOptions.muscles). Regions are listed for the viewer's left
 * side and mirrored, and drawn as smooth curves through their points.
 */
object BodyMapGeometry {
    const val WIDTH = 100f
    const val HEIGHT = 200f

    /** The head, as an oval: centre and radii. */
    val headCenter = BodyPoint(50f, 13f)
    const val HEAD_RADIUS_X = 8.5f
    const val HEAD_RADIUS_Y = 10.5f

    /** Neck, torso, arms, hands, legs and feet, shared by both figures. */
    val silhouette: List<BodyRegion> = listOf(
        one(null, 44f, 21f, 56f, 21f, 57f, 35f, 43f, 35f),
        both(null, 50f, 33f, 39f, 33f, 28f, 36f, 23f, 43f, 22f, 54f, 30f, 57f, 33f, 70f, 35f, 90f, 33f, 103f, 32f, 113f, 50f, 117f),
        both(null, 24f, 42f, 20f, 56f, 19f, 76f, 14f, 92f, 12f, 109f, 18f, 111f, 24f, 95f, 30f, 78f, 31f, 56f),
        both(null, 12f, 108f, 18f, 109f, 19f, 119f, 15f, 126f, 10f, 120f),
        both(null, 32f, 110f, 32f, 136f, 34f, 152f, 34f, 170f, 35f, 188f, 45f, 189f, 46f, 170f, 46f, 153f, 48f, 139f, 50f, 117f),
        both(null, 35f, 187f, 45f, 187f, 47f, 195f, 33f, 197f),
    ).flatten()

    val front: List<BodyRegion> = listOf(
        one("neck", 45f, 24f, 55f, 24f, 56f, 32f, 44f, 32f),
        both("traps", 44f, 31f, 37f, 35f, 44f, 36f),
        both("shoulders", 37f, 35f, 29f, 37f, 24f, 44f, 24f, 52f, 30f, 51f, 33f, 43f),
        both("chest", 49f, 38f, 38f, 37f, 34f, 43f, 34f, 53f, 41f, 58f, 49f, 56f),
        both("biceps", 24f, 54f, 30f, 53f, 30f, 63f, 28f, 74f, 23f, 75f, 21f, 64f),
        both("forearms", 21f, 78f, 28f, 78f, 26f, 90f, 20f, 106f, 14f, 106f, 15f, 93f),
        both("abdominals", 49.5f, 60f, 43f, 60f, 42f, 80f, 44f, 100f, 49.5f, 100f),
        both("abdominals", 35f, 58f, 41f, 61f, 40f, 82f, 42f, 99f, 37f, 97f, 35f, 80f),
        both("abductors", 34f, 103f, 38f, 104f, 37f, 121f, 33f, 117f),
        both("quadriceps", 39f, 106f, 46f, 113f, 44f, 131f, 47f, 140f, 46f, 149f, 39f, 149f, 34f, 138f, 34f, 121f),
        both("adductors", 49f, 116f, 46f, 115f, 45f, 130f, 48f, 137f),
        both("calves", 36f, 156f, 41f, 154f, 41f, 170f, 39f, 183f, 37f, 178f, 35f, 166f),
        both("calves", 44f, 156f, 46f, 158f, 45f, 170f, 43f, 178f, 42f, 166f),
    ).flatten()

    val back: List<BodyRegion> = listOf(
        one("neck", 45f, 23f, 55f, 23f, 55f, 28f, 45f, 28f),
        both("traps", 50f, 25f, 44f, 29f, 37f, 35f, 43f, 38f, 50f, 46f),
        both("shoulders", 37f, 35f, 29f, 37f, 24f, 44f, 24f, 52f, 30f, 50f, 35f, 41f),
        both("triceps", 24f, 54f, 30f, 52f, 31f, 63f, 29f, 75f, 23f, 75f, 21f, 64f),
        both("forearms", 21f, 78f, 28f, 78f, 26f, 90f, 20f, 106f, 14f, 106f, 15f, 93f),
        both("middle back", 49.5f, 48f, 44f, 40f, 38f, 46f, 41f, 57f, 49.5f, 62f),
        both("lats", 37f, 47f, 33f, 54f, 34f, 67f, 39f, 82f, 44f, 85f, 43f, 70f, 40f, 58f),
        both("lower back", 49.5f, 65f, 44f, 73f, 45f, 86f, 43f, 96f, 49.5f, 98f),
        both("abductors", 33f, 97f, 40f, 97f, 36f, 104f, 32f, 106f),
        both("glutes", 49.5f, 101f, 41f, 99f, 35f, 106f, 34f, 118f, 40f, 124f, 49.5f, 121f),
        both("hamstrings", 35f, 125f, 42f, 127f, 48f, 125f, 47f, 140f, 45f, 149f, 38f, 149f, 34f, 138f),
        both("adductors", 49f, 123f, 46f, 125f, 48f, 133f),
        both("calves", 36f, 154f, 42f, 152f, 46f, 156f, 45f, 170f, 41f, 181f, 37f, 173f, 35f, 162f),
    ).flatten()

    /** Every muscle the figures show. */
    val muscles: Set<String> = (front + back).mapNotNull { it.muscle }.toSet()

    /** How strongly a muscle with [sets] is shaded, from 0 (none) through 0.35 (one set) to 1 (the most worked muscle). */
    fun shade(sets: Int, most: Int): Float = if (sets <= 0 || most <= 0) 0f else 0.35f + 0.65f * sets.coerceAtMost(most) / most

    /** The muscle drawn at ([x], [y]) in a figure made of [regions], if any (later regions are on top). */
    fun muscleAt(regions: List<BodyRegion>, x: Float, y: Float): String? =
        regions.lastOrNull { it.muscle != null && contains(it.points, x, y) }?.muscle

    /** A smooth closed curve through [points] (a Catmull-Rom spline), starting at the first point. */
    fun curves(points: List<BodyPoint>): List<BodyCurve> = points.indices.map { i ->
        val n = points.size
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]
        BodyCurve(
            BodyPoint(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f),
            BodyPoint(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f),
            p2,
        )
    }

    /** Even-odd point-in-polygon test. */
    private fun contains(points: List<BodyPoint>, x: Float, y: Float): Boolean {
        var inside = false
        var j = points.lastIndex
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    private fun points(coords: FloatArray): List<BodyPoint> = coords.toList().chunked(2) { (x, y) -> BodyPoint(x, y) }

    /** A region in the middle of the body. */
    private fun one(muscle: String?, vararg coords: Float) = listOf(BodyRegion(muscle, points(coords)))

    /** A region on the viewer's left and its mirror image on the right. */
    private fun both(muscle: String?, vararg coords: Float): List<BodyRegion> {
        val left = points(coords)
        return listOf(BodyRegion(muscle, left), BodyRegion(muscle, left.map { BodyPoint(WIDTH - it.x, it.y) }))
    }
}
