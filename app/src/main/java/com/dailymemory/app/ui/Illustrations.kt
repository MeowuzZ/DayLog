package com.dailymemory.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.cos
import kotlin.math.sin

// Vector artwork stays crisp at every screen density and does not depend on emoji fonts.
enum class Fruit { PLUM, ORANGE, APPLE, DRAGONFRUIT, PEACH }
enum class JournalSymbol { ADD, BACK, NEXT, DOWN, UP, EDIT, DELETE, LIST, CALENDAR, DOWNLOAD, UPLOAD, FOLDER, SHIELD, INFO, STAR, BELL, SORT }

@Composable
fun JournalIcon(
    symbol: JournalSymbol,
    description: String? = null,
    modifier: Modifier = Modifier,
    color: Color = JournalColors.Ink,
) {
    Canvas(modifier.then(if (description == null) Modifier else Modifier.semantics { contentDescription = description })) {
        withTransform({ scale(size.width / 32f, size.height / 32f, Offset.Zero) }) {
            val stroke = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1,y1), Offset(x2,y2), 2.2f, StrokeCap.Round)
            fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), color, style = stroke)
            when (symbol) {
                JournalSymbol.ADD -> { line(16f,5f,16f,27f); line(5f,16f,27f,16f) }
                JournalSymbol.BACK -> path { moveTo(20f,5f); lineTo(9f,16f); lineTo(20f,27f) }
                JournalSymbol.NEXT -> path { moveTo(12f,6f); lineTo(21f,16f); lineTo(12f,26f) }
                JournalSymbol.DOWN -> path { moveTo(7f,12f); lineTo(16f,21f); lineTo(25f,12f) }
                JournalSymbol.UP -> path { moveTo(7f,20f); lineTo(16f,11f); lineTo(25f,20f) }
                JournalSymbol.EDIT -> {
                    path { moveTo(15f,5f); lineTo(7f,5f); quadraticBezierTo(4f,5f,4f,8f); lineTo(4f,25f); quadraticBezierTo(4f,28f,7f,28f); lineTo(24f,28f); quadraticBezierTo(27f,28f,27f,25f); lineTo(27f,17f) }
                    path { moveTo(14f,20f); lineTo(15f,14f); lineTo(26f,3f); lineTo(30f,7f); lineTo(19f,18f); close() }
                }
                JournalSymbol.DELETE -> {
                    line(5f,8f,27f,8f); line(12f,4f,20f,4f)
                    path { moveTo(8f,9f); lineTo(10f,28f); lineTo(23f,28f); lineTo(25f,9f) }
                    line(14f,13f,14f,23f); line(20f,13f,20f,23f)
                }
                JournalSymbol.LIST -> repeat(3) { i ->
                    val y=6f+i*10f; drawCircle(color,1.2f,Offset(3f,y)); line(9f,y,29f,y)
                }
                JournalSymbol.CALENDAR -> {
                    drawRoundRect(color, Offset(4f,6f), Size(24f,23f), CornerRadius(3f), style=stroke)
                    line(4f,13f,28f,13f); line(10f,3f,10f,9f); line(22f,3f,22f,9f)
                    drawCircle(color,2f,Offset(16f,21f))
                }
                JournalSymbol.DOWNLOAD, JournalSymbol.UPLOAD -> {
                    line(5f,28f,27f,28f)
                    if(symbol==JournalSymbol.DOWNLOAD) {
                        path { moveTo(12f,3f); lineTo(20f,3f); lineTo(20f,14f); lineTo(27f,14f); lineTo(16f,24f); lineTo(5f,14f); lineTo(12f,14f); close() }
                    } else {
                        path { moveTo(12f,24f); lineTo(20f,24f); lineTo(20f,13f); lineTo(27f,13f); lineTo(16f,3f); lineTo(5f,13f); lineTo(12f,13f); close() }
                    }
                }
                JournalSymbol.FOLDER -> path { moveTo(3f,9f); lineTo(3f,27f); lineTo(29f,27f); lineTo(29f,9f); lineTo(16f,9f); lineTo(13f,5f); lineTo(3f,5f); close() }
                JournalSymbol.SHIELD -> {
                    path { moveTo(16f,2f); lineTo(28f,7f); lineTo(27f,18f); quadraticBezierTo(25f,26f,16f,30f); quadraticBezierTo(7f,26f,5f,18f); lineTo(4f,7f); close() }
                    path { moveTo(10f,15f); lineTo(14f,20f); lineTo(22f,11f) }
                }
                JournalSymbol.INFO -> { drawCircle(color,12f,Offset(16f,16f),style=stroke); line(16f,14f,16f,24f); drawCircle(color,1.5f,Offset(16f,9f)) }
                JournalSymbol.STAR -> path {
                    repeat(10) { i ->
                        val a=-Math.PI/2+i*Math.PI/5; val r=if(i%2==0)14f else 6f
                        val x=16f+cos(a).toFloat()*r;val y=16f+sin(a).toFloat()*r
                        if(i==0) moveTo(x,y) else lineTo(x,y)
                    }; close()
                }
                JournalSymbol.BELL -> {
                    path { moveTo(5f,24f); lineTo(8f,20f); lineTo(8f,12f); cubicTo(8f,1f,24f,1f,24f,12f); lineTo(24f,20f); lineTo(27f,24f); close() }
                    drawArc(color,0f,180f,false,Offset(13f,24f),Size(6f,6f),style=stroke)
                }
                JournalSymbol.SORT -> {
                    path { moveTo(3f,10f); lineTo(10f,3f); lineTo(10f,29f) }
                    path { moveTo(19f,3f); lineTo(19f,29f); lineTo(28f,22f) }
                }
            }
        }
    }
}

@Composable
fun FruitIcon(fruit: Fruit, cut: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        withTransform({ scale(size.width/100f,size.height/100f,Offset.Zero) }) {
            val ink=JournalColors.Ink
            val outline=Stroke(3.1f,cap=StrokeCap.Round,join=StrokeJoin.Round)
            fun outlined(path: Path, color: Color) { drawPath(path,color);drawPath(path,ink,style=outline) }
            drawOval(JournalColors.Navigation,Offset(9f,85f),Size(82f,12f))
            drawOval(ink,Offset(9f,85f),Size(82f,12f),style=Stroke(2.2f))
            drawLine(ink,Offset(49f,27f),Offset(54f,9f),3f,StrokeCap.Round)
            val leaf=Path().apply { moveTo(52f,20f); cubicTo(48f,10f,55f,3f,67f,5f); cubicTo(67f,16f,62f,21f,52f,20f); close() }
            outlined(leaf,Color(0xFF4A963E))
            drawLine(ink,Offset(54f,18f),Offset(62f,9f),1.5f,StrokeCap.Round)
            when(fruit) {
                Fruit.PLUM -> {
                    val body=Path().apply { moveTo(50f,25f); cubicTo(21f,17f,12f,42f,19f,68f); cubicTo(25f,96f,75f,99f,83f,69f); cubicTo(91f,40f,78f,19f,50f,25f); close() }
                    drawPath(body,Brush.horizontalGradient(listOf(Color(0xFFBF3E77),Color(0xFF982A58)),15f,87f)); drawPath(body,ink,style=outline)
                    if(cut) {
                        drawOval(Color(0xFFFFDA83),Offset(28f,30f),Size(45f,53f));drawOval(ink,Offset(28f,30f),Size(45f,53f),style=Stroke(2.3f))
                        drawOval(Color(0xFF7B293E),Offset(42f,44f),Size(18f,29f));drawOval(Color(0xFFAE5960),Offset(46f,47f),Size(7f,19f))
                    } else {
                        drawPath(Path().apply { moveTo(46f,29f);cubicTo(57f,41f,46f,62f,53f,82f) },ink.copy(alpha=.55f),style=Stroke(2f,cap=StrokeCap.Round))
                        drawPath(Path().apply { moveTo(29f,38f);cubicTo(20f,51f,25f,65f,30f,69f) },Color.White.copy(alpha=.3f),style=Stroke(4f,cap=StrokeCap.Round))
                    }
                }
                Fruit.ORANGE -> {
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC641),Color(0xFFF7A51F)),Offset(41f,44f),47f),35f,Offset(50f,57f))
                    drawCircle(ink,35f,Offset(50f,57f),style=outline)
                    if(cut) {
                        drawCircle(Color(0xFFFFECAB),29f,Offset(50f,57f));drawCircle(Color(0xFFFFC53F),26f,Offset(50f,57f))
                        repeat(9) { i -> val a=i*Math.PI*2/9; drawLine(Color(0xFFFFF5D2),Offset(50f,57f),Offset(50f+cos(a).toFloat()*27f,57f+sin(a).toFloat()*27f),2.5f,StrokeCap.Round) }
                        drawCircle(Color(0xFFFFF5D2),3f,Offset(50f,57f))
                    } else {
                        listOf(34f to 43f,48f to 35f,61f to 43f,70f to 53f,54f to 55f,37f to 59f,29f to 71f,46f to 76f,64f to 73f,58f to 65f,74f to 66f,30f to 51f).forEach { (x,y)->drawCircle(Color(0xFFDD8715),1.1f,Offset(x,y)) }
                        drawArc(Color.White.copy(alpha=.4f),195f,62f,false,Offset(22f,29f),Size(53f,53f),style=Stroke(3f,cap=StrokeCap.Round))
                    }
                }
                Fruit.APPLE -> {
                    val body=Path().apply { moveTo(50f,28f);cubicTo(28f,12f,10f,33f,17f,56f);cubicTo(21f,79f,32f,96f,49f,86f);cubicTo(64f,97f,79f,81f,84f,57f);cubicTo(92f,29f,71f,15f,50f,28f);close() }
                    drawPath(body,Brush.horizontalGradient(listOf(Color(0xFFFF7945),Color(0xFFF05232)),15f,87f));drawPath(body,ink,style=outline)
                    if(cut) {
                        val flesh=Path().apply { moveTo(50f,33f);cubicTo(31f,21f,20f,39f,25f,58f);cubicTo(30f,76f,39f,85f,50f,78f);cubicTo(65f,86f,73f,72f,77f,54f);cubicTo(82f,35f,66f,22f,50f,33f);close() }
                        outlined(flesh,Color(0xFFFFF0CF));drawLine(Color(0xFFEAD2A7),Offset(50f,34f),Offset(50f,77f),1.4f)
                        drawOval(ink,Offset(42f,53f),Size(4f,10f));drawOval(ink,Offset(55f,53f),Size(4f,10f))
                    } else drawPath(Path().apply { moveTo(28f,35f);cubicTo(20f,46f,26f,61f,28f,63f) },Color.White.copy(alpha=.3f),style=Stroke(4f,cap=StrokeCap.Round))
                }
                Fruit.DRAGONFRUIT -> {
                    val body=Path().apply { moveTo(47f,23f);lineTo(42f,11f);lineTo(54f,18f);lineTo(65f,10f);lineTo(64f,26f);cubicTo(91f,36f,84f,58f,88f,65f);lineTo(80f,68f);cubicTo(84f,91f,32f,98f,23f,78f);lineTo(17f,81f);lineTo(21f,65f);cubicTo(14f,44f,27f,25f,47f,23f);close() }
                    outlined(body,Color(0xFFFF7590))
                    if(cut) {
                        drawOval(Color(0xFFFFF1DD),Offset(29f,29f),Size(47f,57f));drawOval(ink,Offset(29f,29f),Size(47f,57f),style=Stroke(2f))
                        repeat(5) { row-> repeat(4) { col-> val x=38f+col*9f+(row%2)*3f;val y=40f+row*9f;if(x<69f)drawOval(ink,Offset(x,y),Size(1.6f,3f)) } }
                    } else {
                        listOf(35f to 34f,60f to 32f,70f to 54f,38f to 60f,53f to 77f).forEach { (x,y)->
                            outlined(Path().apply { moveTo(x,y+9f);lineTo(x-4f,y-5f);lineTo(x+9f,y+3f);close() },Color(0xFF83A846))
                        }
                    }
                }
                Fruit.PEACH -> {
                    val body=Path().apply { moveTo(50f,27f);cubicTo(29f,14f,10f,33f,18f,60f);cubicTo(22f,79f,41f,92f,50f,92f);cubicTo(64f,88f,82f,76f,85f,57f);cubicTo(92f,29f,73f,16f,50f,27f);close() }
                    drawPath(body,Brush.horizontalGradient(listOf(Color(0xFFFFBDD0),Color(0xFFFF839D)),18f,83f));drawPath(body,ink,style=outline)
                    if(cut) {
                        drawOval(Color(0xFFFFE8DF),Offset(27f,31f),Size(49f,51f));drawOval(Color(0xFFECABAE),Offset(27f,31f),Size(49f,51f),style=Stroke(2f))
                        drawOval(ink,Offset(42f,46f),Size(19f,28f));drawOval(Color(0xFFBC716A),Offset(46f,49f),Size(9f,18f))
                    } else {
                        drawPath(Path().apply { moveTo(52f,31f);cubicTo(41f,46f,48f,69f,51f,80f) },Color(0xFFE47189),style=Stroke(2.7f,cap=StrokeCap.Round))
                        drawPath(Path().apply { moveTo(29f,35f);cubicTo(21f,45f,24f,61f,30f,67f) },Color.White.copy(alpha=.5f),style=Stroke(4f,cap=StrokeCap.Round))
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyJournalIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        withTransform({ scale(size.width/160f,size.height/140f,Offset.Zero) }) {
            val fog=Color(0xFFF5F3F2);val pale=Color(0xFFE9E6E5);val grey=Color(0xFFD8D5D3)
            drawOval(fog,Offset(5f,26f),Size(144f,85f));drawOval(fog,Offset(17f,106f),Size(128f,16f))
            drawLine(grey,Offset(64f,98f),Offset(53f,114f),8f,StrokeCap.Round)
            drawLine(grey,Offset(99f,99f),Offset(108f,111f),8f,StrokeCap.Round)
            rotate(-18f,Offset(83f,67f)) {
                drawOval(grey,Offset(42f,24f),Size(72f,88f))
                drawOval(pale,Offset(48f,20f),Size(72f,88f))
                drawOval(Color.White.copy(alpha=.8f),Offset(57f,29f),Size(54f,70f))
                drawOval(pale,Offset(66f,39f),Size(36f,50f))
                drawOval(Color.White.copy(alpha=.8f),Offset(75f,49f),Size(18f,30f))
            }
            drawLine(grey,Offset(87f,65f),Offset(128f,17f),3f,StrokeCap.Round)
            drawPath(Path().apply { moveTo(119f,27f);lineTo(118f,14f);lineTo(133f,1f);lineTo(132f,15f);lineTo(142f,13f);lineTo(129f,27f);close() },pale)
            drawLine(grey,Offset(24f,122f),Offset(52f,130f),2f,StrokeCap.Round)
            drawPath(Path().apply { moveTo(24f,122f);lineTo(16f,113f);lineTo(13f,120f);lineTo(17f,127f);close() },pale)
        }
    }
}
