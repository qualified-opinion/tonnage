package com.janreins.tonnage.ui.components

import kotlin.math.min

data class BodyPoint(val x: Float, val y: Float)

/** One step of an SVG-style outline, in the artwork's own units. */
sealed interface PathStep {
    data class MoveTo(val x: Float, val y: Float) : PathStep
    data class LineTo(val x: Float, val y: Float) : PathStep
    data class CubicTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : PathStep
    data object Close : PathStep
}

enum class ShapeKind { BODY, NEUTRAL, MUSCLE, LINE }

/**
 * One shape of the body art: bare skin, a neutral part (hands, feet, face), a muscle with its
 * [muscle] group and [part] name, or a detail line drawn on top.
 */
class BodyShape(
    val kind: ShapeKind,
    val muscle: String?,
    val part: String?,
    val evenOdd: Boolean,
    val strokeWidth: Float,
    val steps: List<PathStep>,
) {
    /** The outline flattened into polygons (one per sub-path), for hit-testing. */
    val polygons: List<List<BodyPoint>> by lazy { flatten(steps) }

    val left: Float by lazy { polygons.minOf { p -> p.minOf { it.x } } }
    val top: Float by lazy { polygons.minOf { p -> p.minOf { it.y } } }
    val right: Float by lazy { polygons.maxOf { p -> p.maxOf { it.x } } }
    val bottom: Float by lazy { polygons.maxOf { p -> p.maxOf { it.y } } }

    fun contains(x: Float, y: Float): Boolean {
        if (kind == ShapeKind.LINE || x < left || x > right || y < top || y > bottom) return false
        var winding = 0
        var crossings = 0
        polygons.forEach { poly ->
            for (i in poly.indices) {
                val a = poly[i]
                val b = poly[(i + 1) % poly.size]
                if ((a.y <= y) != (b.y <= y)) {
                    val atX = a.x + (y - a.y) / (b.y - a.y) * (b.x - a.x)
                    if (x < atX) {
                        crossings++
                        winding += if (b.y > a.y) 1 else -1
                    }
                }
            }
        }
        return if (evenOdd) crossings % 2 == 1 else winding != 0
    }
}

/** The front or back figure: its size in artwork units and its shapes in drawing order. */
class BodyView(val width: Float, val height: Float, val shapes: List<BodyShape>) {
    /** The muscle under ([x], [y]), or null for skin, hands, face or empty space. */
    fun regionAt(x: Float, y: Float): BodyShape? =
        shapes.lastOrNull { it.kind != ShapeKind.LINE && it.contains(x, y) }?.takeIf { it.kind == ShapeKind.MUSCLE }

    fun muscleAt(x: Float, y: Float): String? = regionAt(x, y)?.muscle
}

/**
 * The anatomy artwork behind the muscle heat map, read from `assets/body_map.txt`. The drawings are
 * by Ryan Graves (CC BY 4.0), with every muscle tagged with the exercise library's muscle group.
 */
class BodyMapArt(val front: BodyView, val back: BodyView) {
    val muscles: Set<String> = (front.shapes + back.shapes).mapNotNull { it.muscle }.toSet()

    companion object {
        const val ASSET_NAME = "body_map.txt"
        const val CREDIT = "By Ryan Graves, licensed CC BY 4.0, via the muscle_mapper project. Adapted for Tonnage: recoloured, " +
            "grouped by muscle and lower back added."

        private val SIZES = mapOf("front" to (587f to 1137f), "back" to (596f to 1133f))

        /** How strongly to shade a muscle: 0 when unworked, rising to 1 for the most-worked muscle. */
        fun shade(sets: Int, most: Int): Float = if (sets <= 0 || most <= 0) 0f else min(sets, most).toFloat() / most

        fun parse(text: String): BodyMapArt {
            val byView = text.lineSequence()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line ->
                    val f = line.split('|')
                    f[0] to BodyShape(
                        kind = when (f[1]) {
                            "B" -> ShapeKind.BODY
                            "N" -> ShapeKind.NEUTRAL
                            "M" -> ShapeKind.MUSCLE
                            "L" -> ShapeKind.LINE
                            else -> error("Unknown shape kind ${f[1]}")
                        },
                        muscle = f[2].ifEmpty { null },
                        part = f[3].ifEmpty { null },
                        evenOdd = f[4] == "E",
                        strokeWidth = f[5].toFloatOrNull() ?: 0f,
                        steps = parsePath(f[6]),
                    )
                }
                .groupBy({ it.first }, { it.second })
            fun view(name: String) = SIZES.getValue(name).let { (w, h) -> BodyView(w, h, byView[name].orEmpty()) }
            return BodyMapArt(view("front"), view("back"))
        }

        /** Reads absolute M, L, H, V, C and Z commands, which is all the artwork uses. */
        fun parsePath(d: String): List<PathStep> {
            val tokens = Regex("[A-Za-z]|-?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?").findAll(d).map { it.value }.toList()
            val steps = mutableListOf<PathStep>()
            var i = 0
            var command = 'M'
            var x = 0f
            var y = 0f
            fun next() = tokens[i++].toFloat()
            while (i < tokens.size) {
                if (tokens[i][0].isLetter()) command = tokens[i++][0]
                when (command) {
                    'M' -> { x = next(); y = next(); steps += PathStep.MoveTo(x, y); command = 'L' }
                    'L' -> { x = next(); y = next(); steps += PathStep.LineTo(x, y) }
                    'H' -> { x = next(); steps += PathStep.LineTo(x, y) }
                    'V' -> { y = next(); steps += PathStep.LineTo(x, y) }
                    'C' -> {
                        val s = PathStep.CubicTo(next(), next(), next(), next(), next(), next())
                        x = s.x; y = s.y; steps += s
                    }
                    'Z', 'z' -> steps += PathStep.Close
                    else -> error("Unsupported path command $command")
                }
            }
            return steps
        }
    }
}

/** Flattens an outline into polygons, one per sub-path, with each curve split into short lines. */
private fun flatten(steps: List<PathStep>): List<List<BodyPoint>> {
    val polygons = mutableListOf<List<BodyPoint>>()
    var current = mutableListOf<BodyPoint>()
    fun finish() {
        if (current.size >= 3) polygons += current
        current = mutableListOf()
    }
    steps.forEach { step ->
        when (step) {
            is PathStep.MoveTo -> { finish(); current += BodyPoint(step.x, step.y) }
            is PathStep.LineTo -> current += BodyPoint(step.x, step.y)
            is PathStep.CubicTo -> {
                val start = current.lastOrNull() ?: BodyPoint(step.x1, step.y1)
                for (n in 1..8) {
                    val t = n / 8f
                    val u = 1 - t
                    current += BodyPoint(
                        u * u * u * start.x + 3 * u * u * t * step.x1 + 3 * u * t * t * step.x2 + t * t * t * step.x,
                        u * u * u * start.y + 3 * u * u * t * step.y1 + 3 * u * t * t * step.y2 + t * t * t * step.y,
                    )
                }
            }
            PathStep.Close -> {
                val first = current.firstOrNull()
                finish()
                if (first != null) current += first
            }
        }
    }
    finish()
    return polygons.ifEmpty { listOf(listOf(BodyPoint(0f, 0f))) }
}
