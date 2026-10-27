package com.usaginoyami.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usaginoyami.vault.logic.DIAS_LIXEIRA

@Composable
fun LixeiraScreen(vm: VaultViewModel) {
    var confirmarEsvaziar by remember { mutableStateOf(false) }
    val agora = remember { System.currentTimeMillis() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.voltar() }) { Text("← Voltar", color = VaultColors.Muted) }
            Text(
                "Lixeira", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = VaultColors.Text,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { confirmarEsvaziar = true }, enabled = vm.lixeira.isNotEmpty()) {
                Text("Esvaziar", color = VaultColors.Drop)
            }
        }
        Text(
            "Jogos excluídos ficam aqui por $DIAS_LIXEIRA dias e depois são apagados de vez.",
            color = VaultColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (vm.lixeira.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("A lixeira está vazia.", color = VaultColors.Muted)
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(vm.lixeira, key = { it.jogo.id }) { e ->
                    val dias = ((agora - e.excluidoEm) / (24L * 3600_000L)).toInt().coerceAtLeast(0)
                    val restam = (DIAS_LIXEIRA - dias).coerceAtLeast(0)
                    val forma = RoundedCornerShape(12.dp)
                    Column(
                        Modifier.fillMaxWidth().background(VaultColors.Surface, forma)
                            .border(1.dp, VaultColors.Line, forma).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            e.jogo.nome, color = VaultColors.Text, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            (e.jogo.plataforma ?: "—") + " · some em $restam ${if (restam == 1) "dia" else "dias"}",
                            color = VaultColors.Muted, fontSize = 12.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.restaurar(e.jogo.id) }) { Text("Restaurar") }
                            TextButton(onClick = { vm.apagarDeVez(e.jogo.id) }) {
                                Text("Apagar de vez", color = VaultColors.Drop)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmarEsvaziar) {
        AlertDialog(
            onDismissRequest = { confirmarEsvaziar = false },
            title = { Text("Esvaziar a lixeira?") },
            text = { Text("${vm.lixeira.size} jogo(s) serão apagados de vez, sem volta.") },
            confirmButton = {
                TextButton(onClick = { confirmarEsvaziar = false; vm.esvaziarLixeira() }) {
                    Text("Esvaziar", color = VaultColors.Drop)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarEsvaziar = false }) { Text("Cancelar") } },
        )
    }
}
