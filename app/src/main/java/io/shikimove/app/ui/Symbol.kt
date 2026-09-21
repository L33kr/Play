package io.shikimove.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable fun Symbol(name: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface, description: String? = null) {
    Canvas(modifier.size(24.dp).semantics { if (description != null) contentDescription = description }) {
        val u = size.width / 24
        val stroke = Stroke(1.7f * u, cap = StrokeCap.Round)
        fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(color, Offset(x*u,y*u),Offset(xx*u,yy*u),strokeWidth=1.7f*u,cap=StrokeCap.Round)
        when (name) {
            "grid" -> listOf(4f to 4f,14f to 4f,4f to 14f,14f to 14f).forEach { (x,y) -> drawRoundRect(color,Offset(x*u,y*u),Size(6*u,6*u),CornerRadius(u),style=stroke) }
            "search" -> { drawCircle(color,6.5f*u,Offset(10*u,10*u),style=stroke);line(15f,15f,21f,21f) }
            "bookmark" -> { val p=Path().apply{moveTo(6*u,3*u);lineTo(18*u,3*u);lineTo(18*u,21*u);lineTo(12*u,17*u);lineTo(6*u,21*u);close()};drawPath(p,color,style=stroke) }
            "clock" -> {drawCircle(color,9*u,Offset(12*u,12*u),style=stroke);line(12f,7f,12f,12f);line(12f,12f,16f,14f)}
            "settings" -> {line(4f,7f,20f,7f);line(4f,17f,20f,17f);drawCircle(Panel,3*u,Offset(9*u,7*u));drawCircle(color,3*u,Offset(9*u,7*u),style=stroke);drawCircle(Panel,3*u,Offset(15*u,17*u));drawCircle(color,3*u,Offset(15*u,17*u),style=stroke)}
            "play" -> {val p=Path().apply{moveTo(8*u,4*u);lineTo(21*u,12*u);lineTo(8*u,20*u);close()};drawPath(p,color)}
            "pause" -> {line(8f,5f,8f,19f);line(16f,5f,16f,19f)}
            "account" -> {drawCircle(color,4*u,Offset(12*u,7*u),style=stroke);drawArc(color,180f,180f,false,Offset(4*u,13*u),Size(16*u,14*u),style=stroke)}
            "back" -> {line(20f,12f,4f,12f);line(4f,12f,11f,5f);line(4f,12f,11f,19f)}
            "close" -> {line(6f,6f,18f,18f);line(18f,6f,6f,18f)}
            "external" -> {line(14f,4f,20f,4f);line(20f,4f,20f,10f);line(20f,4f,10f,14f);line(5f,5f,5f,20f);line(5f,20f,20f,20f);line(20f,20f,20f,15f)}
            "expand" -> {line(4f,9f,4f,4f);line(4f,4f,9f,4f);line(15f,4f,20f,4f);line(20f,4f,20f,9f);line(20f,15f,20f,20f);line(20f,20f,15f,20f);line(9f,20f,4f,20f);line(4f,20f,4f,15f)}
            "chevron" -> {line(8f,5f,15f,12f);line(15f,12f,8f,19f)}
            else -> {drawCircle(color,3*u,center)}
        }
    }
}
