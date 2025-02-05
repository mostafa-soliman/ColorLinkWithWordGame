package com.example.colorlinkwithwordgame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class WordPair(
        val word: String,
        val meaning: String
    )

    private val wordPairs = listOf(
        WordPair("Book", "Novel"),
        WordPair("Pen", "Writing tool"),
        WordPair("School", "Educational institution"),
        WordPair("House", "Home"),
        WordPair("Car", "Automobile"),
        WordPair("Sun", "Star"),
        WordPair("Moon", "Lunar body"),
        WordPair("Sea", "Ocean"),
        WordPair("Tree", "Plant"),
        WordPair("Cat", "Feline"),
        WordPair("Dog", "Canine"),
        WordPair("Apple", "Fruit"),
        WordPair("Water", "H2O"),
        WordPair("Fire", "Flame"),
        WordPair("Air", "Atmosphere"),
        WordPair("Earth", "Planet"),
        WordPair("Flower", "Bloom"),
        WordPair("Friend", "Companion"),
        WordPair("Family", "Relatives"),
        WordPair("Time", "Duration")
    )

    private val paint = Paint().apply {
        strokeWidth = 20f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val dotPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 60f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val rectPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        isAntiAlias = true
    }

    private var grid = mutableListOf<Dot>()
    private var paths = mutableMapOf<Int, Path>()
    private var currentColor: Int? = null
    private var currentPath: Path? = null
    private var currentStart: Dot? = null
    private var currentEnd: Dot? = null
    private var levelComplete = false
    private var currentLevel = 1
    private var levelCompleteListener: (() -> Unit)? = null
    private var rectWidth = 310f
    private var rectHeight = 190f
    private val maxTextWidth = rectWidth - 40f // Padding for text
    private var isInitialized = false


    private val gameColors = listOf(
        Color.RED,
        Color.BLUE,
        Color.YELLOW,
        Color.GREEN,
        Color.MAGENTA,
        Color.CYAN,
        Color.parseColor("#FFA500")  // Orange
    )

    private val touchPoints = mutableListOf<PointF>()
    private var hasMoved = false

    data class LevelConfig(
        val gridSize: Int,
        val dotConnections: List<DotConnection>
    )

    data class DotConnection(
        val dot1Position: Position,
        val dot2Position: Position,
        val colorIndex: Int,
        val word: String = "",
        val meaning: String = ""
    )

    data class Position(val x: Int, val y: Int)

    data class Dot(
        val x: Float,
        val y: Float,
        val color: Int,
        var connected: Boolean = false,
        val text: String = ""
    )

    private fun generateLevelConfig(level: Int): LevelConfig {
        val random = Random(level)
        val gridSize = 6
        val numPairs = min(random.nextInt(3, 5), wordPairs.size)
        val dotConnections = mutableListOf<DotConnection>()

        val usedPositions = mutableSetOf<Position>()
        val shuffledWords = wordPairs.shuffled(random).take(numPairs)

        for (i in 0 until numPairs) {
            var dot1Pos: Position
            var dot2Pos: Position

            do {
                dot1Pos = Position(
                    random.nextInt(1, gridSize + 1),
                    random.nextInt(1, gridSize + 1)
                )
            } while (dot1Pos in usedPositions)

            do {
                dot2Pos = Position(
                    random.nextInt(1, gridSize + 1),
                    random.nextInt(1, gridSize + 1)
                )
            } while (dot2Pos in usedPositions || dot2Pos == dot1Pos)

            usedPositions.add(dot1Pos)
            usedPositions.add(dot2Pos)

            dotConnections.add(
                DotConnection(
                    dot1Pos,
                    dot2Pos,
                    i % gameColors.size,
                    shuffledWords[i].word,
                    shuffledWords[i].meaning
                )
            )
        }

        return LevelConfig(gridSize, dotConnections)
    }

    private val levelConfigurations = mutableMapOf(
        1 to LevelConfig(
            gridSize = 3,
            dotConnections = listOf(
                DotConnection(Position(1, 1), Position(2, 2), 0, "Book", "Novel"),
                DotConnection(Position(1, 2), Position(2, 1), 1, "Pen", "Writing tool"),
                DotConnection(
                    Position(3, 1),
                    Position(3, 2),
                    2,
                    "School",
                    "Educational institution"
                )
            )
        )
    )

    init {
        for (i in 2..100) {
            levelConfigurations[i] = generateLevelConfig(i)
        }
       // setupLevel(1)
    }

    fun setLevelCompleteListener(listener: () -> Unit) {
        levelCompleteListener = listener
    }

    fun setupLevel(level: Int) {
        if (width == 0 || height == 0) {
            currentLevel = level
            return
        }
        currentLevel = level
        grid.clear()
        paths.clear()
        currentColor = null
        currentPath = null
        currentStart = null
        currentEnd = null
        levelComplete = false

        val config = levelConfigurations[level] ?: levelConfigurations[1]!!
        createDotsFromConfig(config)
        invalidate()
    }
    private fun createDotsFromConfig(config: LevelConfig) {
        if (width == 0 || height == 0) return

        grid.clear()
        val cellWidth = width / (config.gridSize + 1f)
        val cellHeight = height / (config.gridSize + 1f)
        val availablePositions = mutableListOf<Pair<Int, Int>>()

        // Create list of all possible positions
        for (x in 1..config.gridSize) {
            for (y in 1..config.gridSize) {
                availablePositions.add(Pair(x, y))
            }
        }

        // Function to check if a rectangle overlaps with existing ones
        fun wouldOverlap(x: Float, y: Float, existingDots: List<Dot>): Boolean {
            val newRect = RectF(
                x - rectWidth / 2,
                y - rectHeight / 2,
                x + rectWidth / 2,
                y + rectHeight / 2
            )

            // Add padding around rectangles
            val padding = 10f
            newRect.left -= padding
            newRect.top -= padding
            newRect.right += padding
            newRect.bottom += padding

            return existingDots.any { dot ->
                val existingRect = RectF(
                    dot.x - rectWidth / 2,
                    dot.y - rectHeight / 2,
                    dot.x + rectWidth / 2,
                    dot.y + rectHeight / 2
                )
                doesRectanglesOverlap(newRect, existingRect)
            }
        }

        // Function to find a valid position that doesn't overlap
        fun findValidPosition(positions: MutableList<Pair<Int, Int>>): Pair<Int, Int>? {
            val tempPositions = positions.toMutableList()
            while (tempPositions.isNotEmpty()) {
                val position = tempPositions.removeAt(Random.nextInt(tempPositions.size))
                val x = position.first * cellWidth
                val y = position.second * cellHeight

                if (!wouldOverlap(x, y, grid)) {
                    positions.remove(position)
                    return position
                }
            }
            return null
        }

        // Create dots ensuring no overlap
        config.dotConnections.forEach { connection ->
            // Find position for first dot
            val pos1 = findValidPosition(availablePositions)
            if (pos1 != null) {
                grid.add(
                    Dot(
                        pos1.first * cellWidth,
                        pos1.second * cellHeight,
                        gameColors[connection.colorIndex],
                        text = formatText(connection.word)
                    )
                )

                // Find position for second dot
                val pos2 = findValidPosition(availablePositions)
                if (pos2 != null) {
                    grid.add(
                        Dot(
                            pos2.first * cellWidth,
                            pos2.second * cellHeight,
                            gameColors[connection.colorIndex],
                            text = formatText(connection.meaning)
                        )
                    )
                }
            }
        }

        // If not all dots could be placed, try with smaller rectangles
        if (grid.size < config.dotConnections.size * 2) {
            rectWidth *= 0.9f
            rectHeight *= 0.9f
            createDotsFromConfig(config) // Retry with smaller rectangles
        }
    }


    private fun getNextSafePosition(positions: MutableList<Pair<Int, Int>>): Pair<Int, Int>? {
        return if (positions.isNotEmpty()) {
            positions.removeAt(0)
        } else null
    }

    private fun formatText(text: String): String {
        // Split text into lines if it's too long
        val words = text.split(" ")
        val result = StringBuilder()
        var currentLine = StringBuilder()

        textPaint.textSize = 45f

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            val measure = textPaint.measureText(testLine)

            if (measure <= maxTextWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (result.isNotEmpty()) result.append("\n")
                result.append(currentLine)
                currentLine = StringBuilder(word)
            }
        }

        if (currentLine.isNotEmpty()) {
            if (result.isNotEmpty()) result.append("\n")
            result.append(currentLine)
        }

        return result.toString()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!isInitialized) {
            isInitialized = true
            setupLevel(currentLevel)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        textPaint.apply {
            color = Color.WHITE
            textSize = 60f
        }

        canvas.drawText("Level $currentLevel", width / 2f, 100f, textPaint)
        val sortedDots = grid.sortedBy { it.y }

        for ((color, path) in paths) {
            paint.color = color
            canvas.drawPath(path, paint)
        }

        currentPath?.let {
            paint.color = currentColor ?: Color.BLACK
            canvas.drawPath(it, paint)
        }

        for (dot in grid) {
//            val rectWidth = 200f
//            val rectHeight = 100f

            val rect = RectF(
                dot.x - rectWidth / 2,
                dot.y - rectHeight / 2,
                dot.x + rectWidth / 2,
                dot.y + rectHeight / 2
            )

//            dotPaint.color = dot.color
//            canvas.drawRoundRect(rect, 10f, 10f, dotPaint)
//
//            textPaint.color = Color.BLACK
//            textPaint.textSize = 40f
//            canvas.drawText(dot.text, dot.x, dot.y + 15f, textPaint)
//
//
//            if (dot.connected) {
//                rectPaint.color = dot.color // Use connection color when connected
//            } else {
//                rectPaint.color = Color.WHITE // White background when not connected
//
////                dotPaint.color = Color.WHITE
////                val innerRect = RectF(
////                    dot.x - rectWidth / 4,
////                    dot.y - rectHeight / 4,
////                    dot.x + rectWidth / 4,
////                    dot.y + rectHeight / 4
////                )
////                canvas.drawRoundRect(innerRect, 5f, 5f, dotPaint)
//            }
            rectPaint.color = if (dot.connected) dot.color else Color.WHITE
            canvas.drawRoundRect(rect, 10f, 10f, rectPaint)
            textPaint.apply {
                color = if (dot.connected) Color.BLACK else Color.DKGRAY
                textSize = 25f
                textAlign = Paint.Align.CENTER
            }
//            canvas.drawText(dot.text, dot.x, dot.y + 15f, textPaint)
            val lines = dot.text.split("\n")
            val textHeight = textPaint.fontSpacing
            val startY = dot.y - ((lines.size - 1) * textHeight / 2)

            lines.forEachIndexed { index, line ->
                canvas.drawText(
                    line,
                    dot.x,
                    startY + (index * textHeight),
                    textPaint
                )
            }
        }
    }
    private fun doesRectanglesOverlap(rect1: RectF, rect2: RectF): Boolean {
        return rect1.left < rect2.right &&
                rect1.right > rect2.left &&
                rect1.top < rect2.bottom &&
                rect1.bottom > rect2.top
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (levelComplete) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> handleTouchStart(event.x, event.y)
            MotionEvent.ACTION_MOVE -> handleTouchMove(event.x, event.y)
            MotionEvent.ACTION_UP -> handleTouchEnd(event.x, event.y)
        }
        return true
    }

    private fun handleTouchStart(x: Float, y: Float) {
        val startDot = findNearestDot(x, y) ?: return

        if (!startDot.connected) {
            currentStart = startDot
            currentColor = startDot.color
            currentPath = Path().apply {
                moveTo(startDot.x, startDot.y)
            }
            hasMoved = false
            touchPoints.clear()
            invalidate()
        }
    }

    private fun handleTouchEnd(x: Float, y: Float) {
        val endDot = findNearestDot(x, y)
        currentStart?.let { start ->
            if (!hasMoved) {
                resetCurrentPath()
                return
            }

            // Add check to prevent connecting dot to itself
            if (endDot != null && endDot.color == start.color && !endDot.connected && endDot != start) {
                if (!doesPathIntersect(touchPoints) && !doesPathPassThroughDot(touchPoints)) {
                    val finalPath = Path().apply {
                        moveTo(start.x, start.y)
                        for (point in touchPoints) {
                            lineTo(point.x, point.y)
                        }
                        lineTo(endDot.x, endDot.y)
                    }
                    paths[start.color] = finalPath
                    start.connected = true
                    endDot.connected = true
                    checkLevelComplete()
                }
            }
            resetCurrentPath()
        }
    }

    private fun doesPathPassThroughDot(points: List<PointF>): Boolean {
        val currentEndDot = findNearestDot(points.last().x, points.last().y)

        for (dot in grid) {
            if (dot == currentStart || dot == currentEndDot || dot.connected) continue

            val dotRadius = 45f
            for (i in 1 until points.size - 1) {
                val point = points[i]

                val distance = kotlin.math.sqrt(
                    (dot.x - point.x) * (dot.x - point.x) +
                            (dot.y - point.y) * (dot.y - point.y)
                )

                if (distance < dotRadius) {
                    return true
                }
            }
        }
        return false
    }

    private fun doesPathIntersect(points: List<PointF>): Boolean {
        // Convert the current path to line segments
        val newSegments = mutableListOf<LineSegment>()
        if (points.size > 1) {
            for (i in 0 until points.size - 1) {
                newSegments.add(
                    LineSegment(
                        points[i].x,
                        points[i].y,
                        points[i + 1].x,
                        points[i + 1].y
                    )
                )
            }
        }

        // Check each existing path for intersections
        for ((_, path) in paths) {
            val pathSegments = getPathSegments(path)
            for (newSeg in newSegments) {
                for (existingSeg in pathSegments) {
                    if (doLinesIntersect(newSeg, existingSeg)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun doLinesIntersect(line1: LineSegment, line2: LineSegment): Boolean {
        fun orientation(p: PointF, q: PointF, r: PointF): Int {
            val value = (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)
            return when {
                value == 0f -> 0
                value > 0 -> 1
                else -> 2
            }
        }

        fun onSegment(p: PointF, q: PointF, r: PointF): Boolean {
            return q.x <= max(p.x, r.x) && q.x >= min(p.x, r.x) &&
                    q.y <= max(p.y, r.y) && q.y >= min(p.y, r.y)
        }

        val p1 = PointF(line1.x1, line1.y1)
        val q1 = PointF(line1.x2, line1.y2)
        val p2 = PointF(line2.x1, line2.y1)
        val q2 = PointF(line2.x2, line2.y2)

        val o1 = orientation(p1, q1, p2)
        val o2 = orientation(p1, q1, q2)
        val o3 = orientation(p2, q2, p1)
        val o4 = orientation(p2, q2, q1)

        if (o1 != o2 && o3 != o4) return true

        if (o1 == 0 && onSegment(p1, p2, q1)) return true
        if (o2 == 0 && onSegment(p1, q2, q1)) return true
        if (o3 == 0 && onSegment(p2, p1, q2)) return true
        if (o4 == 0 && onSegment(p2, q1, q2)) return true

        return false
    }

    private fun getPathSegments(path: Path): List<LineSegment> {
        val segments = mutableListOf<LineSegment>()
        val pm = PathMeasure(path, false)
        val length = pm.length
        val numSegments = 20 // Number of segments to approximate the path

        val pos1 = FloatArray(2)
        val pos2 = FloatArray(2)

        for (i in 0 until numSegments - 1) {
            pm.getPosTan(i * length / numSegments, pos1, null)
            pm.getPosTan((i + 1) * length / numSegments, pos2, null)
            segments.add(LineSegment(pos1[0], pos1[1], pos2[0], pos2[1]))
        }

        return segments
    }

    private fun checkLevelComplete() {
        levelComplete = grid.all { it.connected }
        if (levelComplete) {
            if (currentLevel == 100) {
                AlertDialog.Builder(context)
                    .setTitle("Congratulations!")
                    .setMessage("You've completed all levels! Do you want to start over?")
                    .setPositiveButton("Yes") { _, _ ->
                        setupLevel(1)
                    }
                    .setNegativeButton("No", null)
                    .setCancelable(false)
                    .show()
            } else {
                levelCompleteListener?.invoke()
            }
        }
    }



    private fun findNearestDot(x: Float, y: Float): Dot? {
        val touchRadius = 100f
        return grid.firstOrNull { dot ->
            abs(dot.x - x) < touchRadius && abs(dot.y - y) < touchRadius
        }
    }

    private fun handleTouchMove(x: Float, y: Float) {
        currentPath?.let { path ->
            if (abs(x - (currentStart?.x ?: 0f)) > 10f || abs(
                    y - (currentStart?.y ?: 0f)
                ) > 10f
            ) {
                hasMoved = true
            }

            touchPoints.add(PointF(x, y))
            path.reset()
            path.moveTo(currentStart?.x ?: 0f, currentStart?.y ?: 0f)

            for (point in touchPoints) {
                path.lineTo(point.x, point.y)
            }

            invalidate()
        }
    }

    fun resetCurrentPath() {
        touchPoints.clear()
        currentPath = null
        currentStart = null
        currentColor = null
        hasMoved = false
        invalidate()
    }

    private data class LineSegment(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float
    )

    fun resetLevel() {
        setupLevel(currentLevel)
    }

}
