package com.example.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

@Composable
fun GestureOverlay(
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onVerticalDragLeft: (Float) -> Unit, // delta Y (negative for up, positive for down)
    onVerticalDragRight: (Float) -> Unit,
    onHorizontalDrag: (Float) -> Unit,   // delta X (positive for right, negative for left)
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    var dragDirection by remember { mutableStateOf<DragDirection?>(null) }
    var dragStartX by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val width = size.width
                        if (offset.x < width / 2) {
                            onDoubleTapLeft()
                        } else {
                            onDoubleTapRight()
                        }
                    },
                    onTap = {
                        onTap()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStartX = offset.x
                        dragDirection = null
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val width = size.width
                        if (dragDirection == null) {
                            dragDirection = if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                DragDirection.HORIZONTAL
                            } else {
                                if (dragStartX < width / 2) {
                                    DragDirection.VERTICAL_LEFT
                                } else {
                                    DragDirection.VERTICAL_RIGHT
                                }
                            }
                        }

                        when (dragDirection) {
                            DragDirection.HORIZONTAL -> {
                                onHorizontalDrag(dragAmount.x)
                            }
                            DragDirection.VERTICAL_LEFT -> {
                                // Negative dragAmount.y means swiping up
                                onVerticalDragLeft(-dragAmount.y)
                            }
                            DragDirection.VERTICAL_RIGHT -> {
                                onVerticalDragRight(-dragAmount.y)
                            }
                            else -> {}
                        }
                    },
                    onDragEnd = {
                        dragDirection = null
                        onDragEnd()
                    },
                    onDragCancel = {
                        dragDirection = null
                        onDragEnd()
                    }
                )
            }
    ) {
        content()
    }
}

enum class DragDirection {
    HORIZONTAL, VERTICAL_LEFT, VERTICAL_RIGHT
}
