package com.janreins.tonnage.ui.components

/** A point in one body figure, which is [BodyMapGeometry.WIDTH] by [BodyMapGeometry.HEIGHT]. */
data class BodyPoint(val x: Float, val y: Float)

/**
 * One outlined region of a body figure. [muscle] is the exercise library's muscle group whose sets
 * shade it, and [part] the name of this piece (e.g. "Side delts"); both are null for the plain body
 * drawn underneath.
 */
data class BodyRegion(val muscle: String?, val part: String?, val points: List<BodyPoint>)

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
    val headCenter = BodyPoint(50f, 11.5f)
    const val HEAD_RADIUS_X = 7.6f
    const val HEAD_RADIUS_Y = 9.6f

    /** Neck, torso, arms, hands, legs and feet, shared by both figures. */
    val silhouette: List<BodyRegion> = listOf(
        both(
            null,
            null,
            50f, 19f, 44.6f, 19.5f, 44.6f, 26f, 36f, 29.4f, 28f, 30.8f, 23f, 33.6f, 20.4f, 39f, 19.8f, 46f, 20.4f, 56f,
            20.4f, 64f, 19.8f, 70f, 18.8f, 74f, 17.4f, 80f, 16.4f, 88f, 16.8f, 96f, 17.2f, 101f, 16.2f, 104f, 14.4f, 110f,
            15f, 116f, 17.6f, 119f, 20f, 116f, 21.4f, 110f, 22f, 104f, 23f, 100.5f, 25.4f, 92f, 27.4f, 84f, 28.2f, 77f,
            28.6f, 73f, 30f, 66f, 31.4f, 56f, 33f, 52f, 34.6f, 60f, 35.2f, 70f, 35.6f, 80f, 35.2f, 88f, 33.6f, 96f, 32f, 104f,
            31.4f, 112f, 31.4f, 122f, 32.4f, 134f, 34f, 142f, 34.4f, 148f, 33f, 155f, 32.6f, 163f, 34f, 172f, 36f, 180f,
            37f, 186f, 35.6f, 191f, 35f, 195f, 38f, 197f, 44.6f, 197f, 45.6f, 193f, 45f, 186f, 45f, 180f, 46.6f, 170f,
            47.2f, 160f, 46.4f, 152f, 46.4f, 146f, 47f, 138f, 48f, 126f, 49.2f, 114f, 50f, 111.5f,
        ),
    ).flatten()

    val front: List<BodyRegion> = listOf(
        both("neck", "Neck", 49.6f, 20.4f, 44.8f, 21f, 44.8f, 26.6f, 47f, 29.6f, 49.6f, 30.6f),
        both("traps", "Upper traps", 44.8f, 24.6f, 44.8f, 27.4f, 46.4f, 30.2f, 38f, 31.6f, 30.4f, 31.4f, 36.4f, 29f),
        both("shoulders", "Front delts", 36.6f, 30f, 30.4f, 31f, 27.4f, 34f, 28.4f, 40.4f, 31.4f, 44.6f, 34.2f, 40f, 37.4f, 35.6f),
        both(
            "shoulders",
            "Side delts",
            29.6f, 30.8f, 24.2f, 33.4f, 21f, 38f, 20.2f, 45f, 21f, 51.4f, 24.4f, 52f, 28.6f, 47.4f, 31f, 44f, 28f, 40f,
            27f, 34.4f,
        ),
        both("chest", "Upper chest", 49.6f, 33.4f, 43f, 31.8f, 37.6f, 33.8f, 34.4f, 39.6f, 41f, 41.8f, 49.6f, 42.2f),
        both("chest", "Lower chest", 49.6f, 42.8f, 41f, 42.4f, 34f, 40.4f, 30.6f, 47.8f, 33f, 53.6f, 38f, 56.6f, 44f, 57.6f, 49.6f, 56f),
        both("chest", "Serratus", 32.8f, 53.4f, 35.8f, 56.2f, 35.6f, 59.4f, 33.2f, 58f),
        both("chest", "Serratus", 33.4f, 59.4f, 36f, 60.8f, 35.8f, 64.4f, 34f, 63.6f),
        both(
            "biceps",
            "Biceps (outer head)",
            24.6f, 51.6f, 26.8f, 50.4f, 27.4f, 58f, 26.8f, 70f, 24f, 72.6f, 21f, 70f, 20.6f, 60f, 21.4f, 52.4f,
        ),
        both("biceps", "Biceps (inner head)", 27.4f, 49.6f, 28.6f, 47.6f, 31.6f, 53f, 30.6f, 62f, 28.6f, 71f, 27f, 71f, 27.8f, 58f),
        both("forearms", "Brachioradialis", 19.4f, 74f, 23f, 73.4f, 23.8f, 80f, 21.6f, 92f, 18.2f, 100.8f, 16.8f, 94f, 17f, 85f),
        both(
            "forearms",
            "Forearm flexors",
            23f, 73.4f, 28.4f, 73.6f, 28f, 78f, 26.6f, 86f, 24f, 94f, 22.6f, 100.6f, 18.8f, 100.8f, 21.8f, 92f, 23.8f, 80f,
        ),
        both(
            "abdominals",
            "Abs",
            45.4f, 58.6f, 48.3f, 58.6f, 49.5f, 59.8f, 49.5f, 65.4f, 48.3f, 66.6f, 45.4f, 66.6f, 44.2f, 65.4f, 44.2f, 59.8f,
        ),
        both(
            "abdominals",
            "Abs",
            45.2f, 67.6f, 48.3f, 67.6f, 49.5f, 68.8f, 49.5f, 75.4f, 48.3f, 76.6f, 45.2f, 76.6f, 44f, 75.4f, 44f, 68.8f,
        ),
        both("abdominals", "Abs", 45f, 77.6f, 48.3f, 77.6f, 49.5f, 78.8f, 49.5f, 86f, 48.3f, 87.2f, 45f, 87.2f, 43.8f, 86f, 43.8f, 78.8f),
        both("abdominals", "Lower abs", 44f, 88.2f, 49.5f, 88.2f, 49.5f, 104.6f, 47.4f, 106f, 44.8f, 98f),
        both(
            "abdominals",
            "Obliques",
            36.6f, 62f, 38.6f, 61f, 43.2f, 62f, 42.8f, 75f, 42.8f, 90f, 43f, 100f, 38f, 99.6f, 35f, 92f, 35.6f, 82f, 35.6f, 70f,
        ),
        both("abductors", "Hip abductors", 34.6f, 92f, 38.6f, 99.6f, 37.6f, 108f, 34f, 116f, 31.6f, 112f, 32f, 104f),
        both("adductors", "Adductors", 49.4f, 111f, 46.6f, 105f, 44f, 106.6f, 43.8f, 116f, 45.6f, 126f, 47.8f, 130f, 48.6f, 120f),
        both(
            "quadriceps",
            "Rectus femoris",
            39f, 100.6f, 43.8f, 107f, 44f, 118f, 43f, 132f, 41f, 141f, 38.8f, 138f, 37.6f, 124f, 37.6f, 110f,
        ),
        both(
            "quadriceps",
            "Vastus lateralis",
            37.6f, 110f, 37.6f, 124f, 38.6f, 138f, 37.6f, 145f, 34.4f, 142.6f, 32.6f, 133f, 31.8f, 122f, 33.6f, 116f,
        ),
        both(
            "quadriceps",
            "Vastus medialis",
            44.6f, 122f, 47.4f, 131f, 47.2f, 139.6f, 45f, 145.6f, 41.6f, 144.6f, 41.2f, 140f, 43.4f, 133f,
        ),
        both("calves", "Calves", 34.4f, 152f, 37.4f, 151f, 38f, 160f, 37.2f, 172f, 35.6f, 179f, 33.6f, 170f, 32.8f, 160f),
        both("calves", "Tibialis", 38.4f, 151.6f, 41.2f, 151.4f, 41.6f, 161f, 40.6f, 173f, 38.8f, 178f, 38.6f, 165f),
        both("calves", "Calves", 43.6f, 152f, 46.4f, 151.6f, 47f, 160f, 46.2f, 170f, 44.6f, 175f, 42.8f, 165f),
    ).flatten()

    val back: List<BodyRegion> = listOf(
        both("neck", "Neck", 49.6f, 20f, 44.8f, 20.6f, 44.6f, 25f, 49.6f, 25f),
        both("traps", "Upper traps", 49.6f, 25.6f, 44.6f, 25.2f, 44.2f, 27.8f, 36f, 30f, 31f, 31.6f, 37f, 34f, 43f, 35.6f, 49.6f, 36.4f),
        both("traps", "Middle and lower traps", 49.6f, 37f, 43.4f, 36.4f, 41f, 38.6f, 43.6f, 44f, 46.4f, 51f, 49.6f, 58f),
        both("shoulders", "Rear delts", 30.8f, 31.6f, 36.4f, 35f, 33.4f, 40.6f, 29.4f, 44f, 27.4f, 40f, 27.8f, 34f),
        both(
            "shoulders",
            "Side delts",
            28f, 32.6f, 24.2f, 33.4f, 21f, 38f, 20.2f, 45f, 21f, 51f, 24.6f, 51.6f, 29f, 47.4f, 29.2f, 44.2f, 27.2f, 40f,
            27.2f, 34.4f,
        ),
        both("middle back", "Rotator cuff", 39.8f, 38.6f, 37f, 35.8f, 33.8f, 40.6f, 33.6f, 45.8f, 37.6f, 47.6f, 41.6f, 45f),
        both("middle back", "Rhomboids", 42.4f, 39.4f, 40.8f, 39.2f, 42.2f, 46.2f, 45.4f, 53f, 46f, 51.4f, 43.8f, 44.6f),
        both(
            "lats",
            "Lats",
            33.4f, 46.6f, 37.6f, 48.4f, 41.8f, 46.4f, 45.6f, 54f, 46.4f, 58f, 46f, 64f, 43.6f, 72f, 41f, 82f, 37.8f, 80f,
            35.4f, 70f, 34.8f, 60f, 32.8f, 52f,
        ),
        both("lower back", "Lower back", 49.6f, 59f, 47f, 59.6f, 46f, 66f, 44.2f, 76f, 43f, 86f, 44f, 94f, 49.6f, 96f),
        both("triceps", "Triceps (outer head)", 21f, 52f, 24.6f, 51.6f, 27f, 56f, 26.2f, 66f, 24.4f, 72.4f, 21f, 71f, 20.6f, 62f),
        both(
            "triceps",
            "Triceps (long head)",
            27.6f, 50.6f, 31.4f, 53f, 31f, 62f, 29f, 70f, 28.4f, 73.4f, 25.4f, 72.6f, 26.8f, 66f, 27.6f, 57f,
        ),
        both("forearms", "Forearm extensors", 19.4f, 74f, 23f, 73.4f, 23.8f, 80f, 21.6f, 92f, 18.2f, 100.8f, 16.8f, 94f, 17f, 85f),
        both(
            "forearms",
            "Forearm extensors",
            23f, 73.4f, 28.4f, 73.6f, 28f, 78f, 26.6f, 86f, 24f, 94f, 22.6f, 100.6f, 18.8f, 100.8f, 21.8f, 92f, 23.8f, 80f,
        ),
        both("abductors", "Glute medius", 34.6f, 90f, 42f, 87f, 43.6f, 93f, 38f, 97f, 33.2f, 101f, 32.8f, 95f),
        both(
            "glutes",
            "Glutes",
            49.6f, 96.6f, 44f, 94.6f, 38f, 97f, 33.2f, 101.6f, 31.8f, 110f, 33.6f, 118f, 39.6f, 121.6f, 46f, 120.6f, 49.6f, 116f,
        ),
        both(
            "hamstrings",
            "Biceps femoris",
            33.2f, 119f, 39.6f, 122.6f, 40.2f, 133f, 39.4f, 142f, 37f, 146f, 34.4f, 142.6f, 32.6f, 133f, 31.8f, 124f,
        ),
        both(
            "hamstrings",
            "Semitendinosus",
            41.2f, 122.6f, 46.4f, 121.4f, 46.8f, 128f, 46f, 138f, 44.8f, 144f, 43.4f, 146.2f, 41.2f, 141f, 41f, 132f,
        ),
        both("adductors", "Adductor magnus", 47.6f, 121.6f, 49.4f, 120.4f, 49.2f, 130f, 47.6f, 136f, 47.4f, 128f),
        both("calves", "Calves (outer)", 34.4f, 151f, 39.4f, 149.6f, 40.4f, 158f, 39.6f, 168f, 37f, 173f, 33.8f, 165f, 33f, 157f),
        both("calves", "Calves (inner)", 41f, 149.6f, 46.4f, 151f, 47f, 158f, 46.2f, 166f, 43.4f, 173.6f, 41f, 168f, 40.6f, 158f),
        both("calves", "Soleus", 35.6f, 174f, 39.6f, 173.6f, 43.6f, 174.6f, 44.6f, 180f, 43f, 185f, 40f, 186.6f, 37f, 184f, 35.6f, 180f),
    ).flatten()

    /** Every muscle the figures show. */
    val muscles: Set<String> = (front + back).mapNotNull { it.muscle }.toSet()

    /** How strongly a muscle with [sets] is shaded, from 0 (none) through 0.35 (one set) to 1 (the most worked muscle). */
    fun shade(sets: Int, most: Int): Float = if (sets <= 0 || most <= 0) 0f else 0.35f + 0.65f * sets.coerceAtMost(most) / most

    /** The muscle region drawn at ([x], [y]) in a figure made of [regions], if any (later regions are on top). */
    fun regionAt(regions: List<BodyRegion>, x: Float, y: Float): BodyRegion? =
        regions.lastOrNull { it.muscle != null && contains(it.points, x, y) }

    /** The muscle group drawn at ([x], [y]), if any. */
    fun muscleAt(regions: List<BodyRegion>, x: Float, y: Float): String? = regionAt(regions, x, y)?.muscle

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

    /** A region on the viewer's left and its mirror image on the right. */
    private fun both(muscle: String?, part: String?, vararg coords: Float): List<BodyRegion> {
        val left = points(coords)
        return listOf(BodyRegion(muscle, part, left), BodyRegion(muscle, part, left.map { BodyPoint(WIDTH - it.x, it.y) }))
    }
}
