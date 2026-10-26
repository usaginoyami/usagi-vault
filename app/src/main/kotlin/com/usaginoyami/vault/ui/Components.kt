package com.usaginoyami.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Cartãozinho de estatística (ex.: 176 / zerados). */
@Composable
fun StatChip(valor: String, rotulo: String) {
    Column(
        Modifier
            .background(VaultColors.Surface, RoundedCornerShape(12.dp))
            .border(1.dp, VaultColors.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(valor, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VaultColors.Text)
        Text(rotulo, fontSize = 11.sp, color = VaultColors.Muted)
    }
}

/** Etiqueta no canto da capa. */
@Composable
fun Selo(texto: String, fundo: Color, letra: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(fundo, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(texto, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = letra)
    }
}

/** Camada escura com um círculo girando, para operações demoradas. */
@Composable
fun CamadaOcupado(texto: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = VaultColors.Red)
            Text(texto, color = VaultColors.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
