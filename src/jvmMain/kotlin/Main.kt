import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.*

// ملف تشخيص بسيط بيتكتب على سطح المكتب عشان نعرف بالظبط فين المشكلة لو حصلت
val debugLogFile = File(System.getProperty("user.home"), "Desktop/Amr3D_debug_log.txt")
fun logDebug(msg: String) {
    try {
        val ts = SimpleDateFormat("HH:mm:ss.SSS").format(Date())
        debugLogFile.appendText("[$ts] $msg\n")
    } catch (_: Exception) { }
}

// ====================== دالة تشغيل التطبيق الرئيسية ======================
fun main() = application {
    debugLogFile.writeText("=== بدء تشغيل Amr3D Nesting Pro ===\n")
    logDebug("main() started")
    Window(onCloseRequest = ::exitApplication, title = "Amr3D Nesting Pro") {
        // الواجهة الحقيقية (index.html) بقت شغالة جوه نافذة الـ EXE عن طريق JavaFX WebView
        // مدموجة مع Compose Desktop باستخدام SwingPanel (JFXPanel)
        AppWebView()
    }
}

@Composable
fun AppWebView() {
    SwingPanel(
        modifier = Modifier.fillMaxSize(),
        factory = {
            logDebug("SwingPanel factory called - creating JFXPanel")
            // JFXPanel لازم يتعمل على الـ EDT (SwingPanel factory بيشتغل عليه أصلاً)
            val jfxPanel = JFXPanel()
            Platform.setImplicitExit(false)
            Platform.runLater {
                try {
                    logDebug("Platform.runLater started")
                    val webView = WebView()
                    val engine = webView.engine

                    // بنسجّل أي خطأ تحميل أو استثناء جوه محرك الـ WebView
                    engine.loadWorker.exceptionProperty().addListener { _, _, ex ->
                        if (ex != null) {
                            val sw = StringWriter()
                            ex.printStackTrace(PrintWriter(sw))
                            logDebug("WebEngine EXCEPTION:\n$sw")
                        }
                    }
                    engine.loadWorker.stateProperty().addListener { _, _, newState ->
                        logDebug("WebEngine state -> $newState")
                        if (newState == Worker.State.FAILED) {
                            logDebug("WebEngine load FAILED. location=${engine.location}")
                        }
                    }

                    // index.html متضمّن جوه الـ resources وبيتقرأ من الـ classpath
                    val htmlUrl = object {}.javaClass.getResource("/index.html")
                    logDebug("Resource lookup /index.html -> $htmlUrl")
                    if (htmlUrl != null) {
                        engine.load(htmlUrl.toExternalForm())
                    } else {
                        logDebug("index.html NOT FOUND on classpath - loading fallback message")
                        engine.loadContent(
                            "<html dir='rtl'><body style='font-family:Tahoma;color:#c00;padding:20px'>" +
                                "تعذّر العثور على index.html ضمن ملفات التطبيق" +
                                "</body></html>"
                        )
                    }
                    jfxPanel.scene = Scene(webView)
                    logDebug("Scene assigned to JFXPanel")
                } catch (ex: Exception) {
                    val sw = StringWriter()
                    ex.printStackTrace(PrintWriter(sw))
                    logDebug("EXCEPTION inside Platform.runLater:\n$sw")
                }
            }
            jfxPanel
        }
    )
}

@Composable
fun LoadingFallback() {
    MaterialTheme {
        Text(
            text = "جاري تشغيل محرك Amr3D الرص التلقائي بنجاح...",
            modifier = Modifier.fillMaxSize(),
            style = MaterialTheme.typography.h5
        )
    }
}

// ====================== كود محرك الرص الخاص بك (Data Classes) ======================
data class NestingPoint(val x: Double, val y: Double)

data class NestingPolygon(
    val outer: List<NestingPoint>,
    val holes: List<List<NestingPoint>> = emptyList()
)

data class NestingPiece(
    val index: Int,
    val polygon: NestingPolygon,
    val x: Double,
    val y: Double,
    val rotationDeg: Double,
    val boundsWidth: Double,
    val boundsHeight: Double
)

data class NestingBoard(
    val index: Int,
    val width: Double,
    val height: Double,
    val pieces: List<NestingPiece>,
    val color: Int = 0xFF0D0F14.toInt()
)

data class NestingResult(
    val boards: List<NestingBoard>,
    val totalRequested: Int,
    val totalPlaced: Int,
    val sourceWidth: Double,
    val sourceHeight: Double,
    val sourceArea: Double,
    val elapsedMs: Long
) {
    val boardArea: Double get() = boards.sumOf { it.width * it.height }
    val usedArea: Double get() = sourceArea * totalPlaced
    val utilization: Double get() = if (boardArea > 0.0) usedArea / boardArea * 100.0 else 0.0
    val wasteArea: Double get() = (boardArea - usedArea).coerceAtLeast(0.0)
}

data class NestingConfig(
    val boardWidth: Double = 1220.0,
    val boardHeight: Double = 2440.0,
    val copies: Int = 1,
    val rotationStepDeg: Double = 15.0,
    val rotationMode: RotationMode = RotationMode.FREE,
    val grainAxis: GrainAxis = GrainAxis.FREE,
    val clearanceMm: Double = 0.0,
    val boardColor: Int = 0xFF0D0F14.toInt(),
    val edgeTopMm: Double = 0.0,
    val edgeBottomMm: Double = 0.0,
    val edgeLeftMm: Double = 0.0,
    val edgeRightMm: Double = 0.0
)

enum class RotationMode { FREE, HORIZONTAL, VERTICAL }
enum class GrainAxis { FREE, HORIZONTAL, VERTICAL }
enum class NestingStage { NESTING, SAVING, PREVIEW }

data class NestingProgress(
    val placed: Int,
    val total: Int,
    val boardIndex: Int,
    val percent: Int,
    val stage: NestingStage = NestingStage.NESTING,
    val stagePercent: Int = percent,
    val stageLabel: String = "جاري الرص"
)

// الكلاسات التخيلية لـ DXF لضمان عمل الكود دون أخطاء بناء
class DxfModel(
    val lines: List<DxfLine> = emptyList(),
    val circles: List<DxfCircle> = emptyList(),
    val arcs: List<DxfArc> = emptyList()
)
class DxfLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
class DxfCircle(val cx: Float, val cy: Float, val r: Float)
class DxfArc(val cx: Float, val cy: Float, val r: Float, val startDeg: Float, val endDeg: Float)

// ====================== خوارزمية بناء الأشكال (Shape Builder) ======================
object NestingShapeBuilder {
    private const val EPS = 0.05
    fun distance(a: NestingPoint, b: NestingPoint) = sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))
    fun signedArea(p: List<NestingPoint>): Double {
        var area = 0.0
        for (i in p.indices) {
            val j = (i + 1) % p.size
            area += p[i].x * p[j].y - p[j].x * p[i].y
        }
        return area / 2.0
    }
    fun pointInPolygon(pt: NestingPoint, poly: List<NestingPoint>): Boolean {
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            if (((poly[i].y > pt.y) != (poly[j].y > pt.y)) &&
                (pt.x < (poly[j].x - poly[i].x) * (pt.y - poly[i].y) / (poly[j].y - poly[i].y) + poly[i].x)) {
                c = !c
            }
            j = i
        }
        return c
    }
    fun normalizedSpan(start: Double, end: Double): Double {
        var res = end - start
        while (res < 0) res += 360.0
        return res
    }

    fun fromModel(model: DxfModel): NestingPolygon? {
        val segments = mutableListOf<Pair<NestingPoint, NestingPoint>>()
        for (l in model.lines) {
            val a = NestingPoint(l.x1.toDouble(), l.y1.toDouble())
            val b = NestingPoint(l.x2.toDouble(), l.y2.toDouble())
            if (distance(a, b) > EPS) segments += a to b
        }
        for (c in model.circles) {
            val pts = circlePoints(c.cx.toDouble(), c.cy.toDouble(), c.r.toDouble(), 96)
            for (i in pts.indices) segments += pts[i] to pts[(i + 1) % pts.size]
        }
        for (a in model.arcs) {
            val span = normalizedSpan(a.startDeg.toDouble(), a.endDeg.toDouble())
            val steps = max(8, min(500, ceil(abs(span) / 7.5).toInt()))
            val pts = (0..steps).map { i ->
                val d = a.startDeg.toDouble() + span * i / steps
                val r = Math.toRadians(d)
                NestingPoint(a.cx.toDouble() + a.r.toDouble() * cos(r), a.cy.toDouble() + a.r.toDouble() * sin(r))
            }
            for (i in 0 until pts.size - 1) segments += pts[i] to pts[i + 1]
        }
        if (segments.isEmpty()) return null
        val loops = traceFaces(segments).map { cleanLoop(it) }.filter { it.size >= 3 && abs(signedArea(it)) > 0.01 }
        if (loops.isEmpty()) return null
        val outer = loops.maxByOrNull { abs(signedArea(it)) } ?: return null
        val holes = loops.filter { it !== outer }.filter { signedArea(it) * signedArea(outer) < 0.0 }.filter { it.isNotEmpty() && pointInPolygon(it[0], outer) }.map { normalizeWinding(it, wantPositive = signedArea(outer) < 0.0) }
        val woundOuter = normalizeWinding(outer, true)
        val minX = woundOuter.minOfOrNull { it.x } ?: 0.0
        val minY = woundOuter.minOfOrNull { it.y } ?: 0.0
        val outerNorm = woundOuter.map { NestingPoint(it.x - minX, it.y - minY) }
        val holeNorm = holes.map { h -> h.map { NestingPoint(it.x - minX, it.y - minY) } }
        return NestingPolygon(outer = outerNorm, holes = holeNorm)
    }

    private fun traceFaces(segments: List<Pair<NestingPoint, NestingPoint>>): List<List<NestingPoint>> {
        val points = mutableListOf<NestingPoint>()
        val cellSize = EPS * 2.0
        val grid = HashMap<Long, MutableList<Int>>()
        fun cellKey(cx: Int, cy: Int) = (cx.toLong() shl 32) xor (cy.toLong() and 0xffffffffL)
        fun cellOf(p: NestingPoint) = floor(p.x / cellSize).toInt() to floor(p.y / cellSize).toInt()
        fun pointId(p: NestingPoint): Int {
            val (cx, cy) = cellOf(p)
            for (dx in -1..1) for (dy in -1..1) {
                val bucket = grid[cellKey(cx + dx, cy + dy)] ?: continue
                for (idx in bucket) if (distance(points[idx], p) <= EPS) return idx
            }
            points += p
            val newIdx = points.lastIndex
            grid.getOrPut(cellKey(cx, cy)) { mutableListOf() } += newIdx
            return newIdx
        }
        data class Edge(val a: Int, val b: Int)
        val edges = segments.map { Edge(pointId(it.first), pointId(it.second)) }
        if (edges.isEmpty()) return emptyList()
        data class Half(val from: Int, val to: Int, val edge: Int)
        val half = mutableListOf<Half>()
        val outgoing = Array(points.size) { mutableListOf<Int>() }
        for ((ei, e) in edges.withIndex()) {
            val h0 = half.size
            half += Half(e.a, e.b, ei)
            half += Half(e.b, e.a, ei)
            outgoing[e.a] += h0
            outgoing[e.b] += h0 + 1
        }
        val order = outgoing.map { list -> list.sortedWith(compareBy { atan2(points[half[it].to].y - points[half[it].from].y, points[half[it].to].x - points[half[it].from].x) }) }
        val next = IntArray(half.size) { -1 }
        for (h in half.indices) {
            val v = half[h].to
            val list = order[v]
            val reverse = list.indexOfFirst { half[it].to == half[h].from }
            if (reverse >= 0) next[h] = list[(reverse - 1 + list.size) % list.size]
        }
        val visited = BooleanArray(half.size)
        val faces = mutableListOf<List<NestingPoint>>()
        for (start in half.indices) {
            if (visited[start] || next[start] < 0) continue
            val loop = mutableListOf<NestingPoint>()
            var h = start
            var guard = 0
            while (!visited[h] && guard++ < half.size + 4) {
                visited[h] = true
                loop += points[half[h].from]
                h = next[h]
                if (h == start) break
            }
            if (h == start && loop.size >= 3) faces += loop
        }
        return faces
    }

    private fun cleanLoop(loop: List<NestingPoint>): List<NestingPoint> {
        val out = mutableListOf<NestingPoint>()
        for (p in loop) if (out.isEmpty() || distance(out.last(), p) > EPS) out += p
        if (out.size > 1 && distance(out.first(), out.last()) <= EPS) out.removeAt(out.lastIndex)
        return out
    }
    private fun normalizeWinding(p: List<NestingPoint>, wantPositive: Boolean): List<NestingPoint> {
        val a = signedArea(p)
        return if ((a > 0) == wantPositive) p else p.asReversed()
    }
    private fun circlePoints(cx: Double, cy: Double, r: Double, n: Int) =
        (0 until n).map {
            val a = 2.0 * PI * it / n
            NestingPoint(cx + r * cos(a), cy + r * sin(a))
        }
}
