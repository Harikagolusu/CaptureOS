package com.google.ai.edge.gallery.customtasks.captureos

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.R

/** CaptureOS design tokens. */
object CaptureColors {
  val Navy = Color(0xFF1B1F4B)
  val Ink = Color(0xFF14162E)
  val Paper = Color(0xFFFAF9F6)
  val High = Color(0xFFB4472F)
  val Medium = Color(0xFFC98A3A)
  val Low = Color(0xFF4C7A63)
  val Neutral = Color(0xFF8A8CA0)
  val Hairline = Color(0x1F8A8CA0)
}

private val Lora =
  FontFamily(
    Font(R.font.lora, FontWeight.Normal),
    Font(R.font.lora, FontWeight.Medium),
    Font(R.font.lora, FontWeight.Bold),
  )

private val Inter =
  FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
  )

val CaptureTypography =
  Typography(
    headlineLarge =
      TextStyle(
        fontFamily = Lora,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        color = CaptureColors.Ink,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = Lora,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        color = CaptureColors.Ink,
      ),
    titleLarge =
      TextStyle(
        fontFamily = Lora,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = CaptureColors.Ink,
      ),
    titleMedium =
      TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = CaptureColors.Ink,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = CaptureColors.Ink,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = CaptureColors.Ink,
      ),
    bodySmall =
      TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = CaptureColors.Neutral,
      ),
    labelLarge =
      TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
      ),
  )

private val CaptureScheme =
  lightColorScheme(
    primary = CaptureColors.Navy,
    onPrimary = CaptureColors.Paper,
    background = CaptureColors.Paper,
    onBackground = CaptureColors.Ink,
    surface = CaptureColors.Paper,
    onSurface = CaptureColors.Ink,
    surfaceVariant = CaptureColors.Paper,
    onSurfaceVariant = CaptureColors.Neutral,
    outline = CaptureColors.Neutral,
    error = CaptureColors.High,
    onError = CaptureColors.Paper,
  )

@Composable
fun CaptureTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = CaptureScheme, typography = CaptureTypography, content = content)
}
