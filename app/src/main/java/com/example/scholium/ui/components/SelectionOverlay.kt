package com.example.scholium.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun SelectionOverlay(
    modifier: Modifier = Modifier,               // ✅ This fixes "No parameter modifier found"
    onSelectionFinished: (Rect) -> Unit          // ✅ This fixes "No parameter onSelectionFinished found"
) {
    var startOffset by remember { mutableStateOf<Offset?>(null) }
    var currentOffset by remember { mutableStateOf<Offset?>(null) }
    var finalRect by remember { mutableStateOf<Rect?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start ->
                        startOffset = start
                        currentOffset = start
                        finalRect = null
                    },
                    onDrag = { change, _ ->
                        currentOffset = change.position
                    },
                    onDragEnd = {
                        if (startOffset != null && currentOffset != null) {
                            val rect = Rect(startOffset!!, currentOffset!!)
                            finalRect = rect
                            onSelectionFinished(rect)
                        }
                    }
                )
            }
    ) {
        if (startOffset != null && currentOffset != null && finalRect == null) {
            val rect = Rect(startOffset!!, currentOffset!!)
            drawSelectionBox(rect)
        }
        finalRect?.let { rect ->
            drawSelectionBox(rect)
        }
    }
}

// Helper function to draw the box
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectionBox(rect: Rect) {
    drawRect(
        color = Color(0xFF3F51B5).copy(alpha = 0.2f),
        topLeft = rect.topLeft,
        size = rect.size
    )
    drawRect(
        color = Color(0xFF3F51B5),
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = 3.dp.toPx())
    )
}