package com.zkrwatch.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/** Zkr brand palette (from the official app): orange accent, green battery. */
val ZkrOrange = Color(0xFFEE6A2D)
val ZkrGreen = Color(0xFF4FD46A)
val ZkrGrey = Color(0xFF9AA0A6)

private val ZkrColors = Colors(
    primary = ZkrOrange,
    primaryVariant = Color(0xFFC9541F),
    secondary = Color(0xFF23272E),
    secondaryVariant = Color(0xFF31363F),
    background = Color.Black,
    surface = Color(0xFF16191F),
    error = Color(0xFFCF5B4E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = ZkrGrey,
    onError = Color.White,
)

@Composable
fun ZkrTheme(content: @Composable () -> Unit) =
    MaterialTheme(colors = ZkrColors, content = content)
