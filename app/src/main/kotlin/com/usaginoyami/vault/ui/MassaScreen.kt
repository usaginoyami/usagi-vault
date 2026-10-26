package com.usaginoyami.vault.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usaginoyami.vault.logic.candidatosMassa
import com.usaginoyami.vault.logic.chamadasLoja

@Composable
fun MassaScreen(vm: VaultViewModel) {
    val o = vm.massaOpcoes
    val rodando = vm.massaRodando
    val candidatos = remember(vm.jogos, o) { candidatosMassa(vm.jogos, o) }
    val minutos = remember(candidatos, o) {
        val segundos = chamadasLoja(candidatos, o) * 1.5 + candidatos.size * 0.5
        Math.ceil(segundos / 60.0).toInt().coerceAtLeast(1)
    }
    val semCapa = remember(vm.jogos) { vm.jogos.count { it.capa == null } }
    val semData = remember(vm.jogos) { vm.jogos.count { it.lancamento == null } }

    // mantém a tela ligada enquanto o lote roda
    val view = LocalView.current
    DisposableEffect(rodando) {
        view.keepScreenOn = rodando
        onDispose { view.keepScreenOn = false }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.voltar() }, enabled = !rodando) { Text("✕ Fechar", color = VaultColors.Muted) }
            Text(
                "Capas em massa",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = VaultColors.Text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text("", Modifier.padding(horizontal = 40.dp))
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Procura cada jogo na Steam pelo nome e preenche a capa e a data de lançamento. " +
                    "Hoje: $semCapa jogos sem capa e $semData sem data de lançamento.",
                color = VaultColors.Muted,
                fontSize = 13.sp,
            )

            Opcao(
                "Baixar capas dos jogos sem capa", null,
                o.capas, !rodando,
            ) { vm.massaOpcoes = o.copy(capas = it) }
            Opcao(
                "Substituir também as capas que já existem", "Troca pela capa da Steam (retrato).",
                o.substituir, !rodando && o.capas,
            ) { vm.massaOpcoes = o.copy(substituir = it) }
            Opcao(
                "Buscar data de lançamento", "Só de quem ainda não tem. Serve para ordenar por lançamento.",
                o.lancamento, !rodando,
            ) { vm.massaOpcoes = o.copy(lancamento = it) }
            Opcao(
                "Incluir jogos de console (PS1, PS2, GBA...)", "A capa da Steam é a do PC e pode não ser a do console.",
                o.consoles, !rodando,
            ) { vm.massaOpcoes = o.copy(consoles = it) }

            if (!rodando) {
                Text(
                    if (candidatos.isEmpty()) "Nada a fazer com essas opções."
                    else "${candidatos.size} jogos a processar · uns $minutos min. Deixe o app aberto.",
                    color = VaultColors.Text,
                    fontSize = 14.sp,
                )
                Button(
                    onClick = { vm.massaIniciar() },
                    enabled = candidatos.isNotEmpty() && (o.capas || o.lancamento),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Começar") }
            } else {
                LinearProgressIndicator(
                    progress = { vm.massaFracao },
                    color = VaultColors.Red,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(vm.massaProgresso, color = VaultColors.Text, fontSize = 13.sp)
                OutlinedButton(onClick = { vm.massaParar() }, modifier = Modifier.fillMaxWidth()) { Text("Parar") }
            }

            vm.massaResumo?.let { Text(it, color = VaultColors.Text, fontWeight = FontWeight.SemiBold) }

            if (vm.massaNaoAchados.isNotEmpty()) {
                Text(
                    "Não encontrei na Steam (abra o jogo e use \"Buscar na Steam\" ou escolha a capa na galeria):",
                    color = VaultColors.Muted,
                    fontSize = 12.sp,
                )
                vm.massaNaoAchados.forEach { Text("• $it", color = VaultColors.Text, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun Opcao(titulo: String, detalhe: String?, marcado: Boolean, ativo: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = ativo) { aoMudar(!marcado) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = marcado,
            onCheckedChange = null,
            enabled = ativo,
            colors = CheckboxDefaults.colors(checkedColor = VaultColors.Red),
        )
        Column(Modifier.weight(1f)) {
            Text(titulo, color = if (ativo) VaultColors.Text else VaultColors.Muted, fontSize = 14.sp)
            if (detalhe != null) Text(detalhe, color = VaultColors.Muted, fontSize = 12.sp)
        }
    }
}
