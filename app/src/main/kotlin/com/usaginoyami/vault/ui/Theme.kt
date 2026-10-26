package com.usaginoyami.vault.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Identidade UsagiNoYami: preto e vermelho. */
object VaultColors {
    val Bg = Color(0xFF0B0B0C)
    val Surface = Color(0xFF161618)
    val Surface2 = Color(0xFF212125)
    val Line = Color(0xFF2E2E33)
    val Text = Color(0xFFEEEAEA)
    val Muted = Color(0xFF9D979B)
    val Red = Color(0xFFC1121F)
    val RedBright = Color(0xFFE0192B)
    val Ok = Color(0xFF3DDC97)
    val OnOk = Color(0xFF06210F)
    val Infinito = Color(0xFF4F7CAC)
    val Drop = Color(0xFFFF6B81)
    val OnDrop = Color(0xFF2A0610)
}

@Composable
fun VaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = VaultColors.Red,
            onPrimary = Color.White,
            secondary = VaultColors.RedBright,
            onSecondary = Color.White,
            background = VaultColors.Bg,
            onBackground = VaultColors.Text,
            surface = VaultColors.Surface,
            onSurface = VaultColors.Text,
            surfaceVariant = VaultColors.Surface2,
            onSurfaceVariant = VaultColors.Muted,
            outline = VaultColors.Line,
            error = VaultColors.Drop,
        ),
        content = content,
    )
}
