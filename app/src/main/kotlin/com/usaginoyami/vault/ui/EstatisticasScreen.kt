package com.usaginoyami.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usaginoyami.vault.logic.fmt
import com.usaginoyami.vault.logic.horasPorGenero
import com.usaginoyami.vault.logic.sequenciaMeses
import com.usaginoyami.vault.logic.zeradosNoAno
import com.usaginoyami.vault.logic.zeradosPorAno
import java.time.LocalDate

@Composable
fun EstatisticasScreen(vm: VaultViewModel) {
    val hoje = remember { LocalDate.now() }
    val porAno = remember(vm.jogos) { zeradosPorAno(vm.jogos) }
    val generos = remember(vm.jogos) { horasPorGenero(vm.jogos) }
    val (seqAtual, seqMelhor) = remember(vm.jogos) { sequenciaMeses(vm.jogos, hoje) }
    val noAno = remember(vm.jogos) { zeradosNoAno(vm.jogos, hoje.year) }
    var metaTexto by remember(vm.metaAnual) { mutableStateOf(if (vm.metaAnual > 0) vm.metaAnual.toString() else "") }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.voltar() }) { Text("← Voltar", color = VaultColors.Muted) }
            Text(
                "Estatísticas", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = VaultColors.Text,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            Box(Modifier.width(72.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Cartao("Meta de ${hoje.year}") {
                OutlinedTextField(
                    value = metaTexto,
                    onValueChange = { novo ->
                        val limpo = novo.filter(Char::isDigit).take(4)
                        metaTexto = limpo
                        vm.definirMeta(limpo.toIntOrNull() ?: 0)
                    },
                    label = { Text("Quantos jogos quer zerar neste ano?") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (vm.metaAnual > 0) {
                    val frac = (noAno.toFloat() / vm.metaAnual).coerceIn(0f, 1f)
                    Barra(frac)
                    val faltam = vm.metaAnual - noAno
                    Text(
                        if (faltam > 0) "$noAno de ${vm.metaAnual} zerados · faltam $faltam" else "Meta batida! $noAno zerados 🎉",
                        color = VaultColors.Text, fontSize = 13.sp,
                    )
                    if (faltam > 0) {
                        val diasRestantes = (hoje.lengthOfYear() - hoje.dayOfYear + 1)
                        val ritmo = faltam * 30.0 / diasRestantes
                        Text(
                            "Para chegar lá: ${fmt(ritmo)} jogos por mês até 31/12.",
                            color = VaultColors.Muted, fontSize = 12.sp,
                        )
                    }
                } else {
                    Text("Sem meta definida. Digite um número para acompanhar o ano.", color = VaultColors.Muted, fontSize = 12.sp)
                }
            }

            Cartao("Sequência (meses seguidos zerando algo)") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("$seqAtual", "mês(es) agora")
                    StatChip("$seqMelhor", "melhor sequência")
                }
            }

            Cartao("Zerados por ano") {
                if (porAno.isEmpty()) {
                    Text("Nenhum jogo zerado com data ainda.", color = VaultColors.Muted, fontSize = 13.sp)
                } else {
                    val max = porAno.maxOf { it.second }
                    porAno.forEach { (ano, qtd) -> LinhaBarra(ano.toString(), qtd.toString(), qtd.toFloat() / max) }
                }
            }

            Cartao("Horas por gênero") {
                if (generos.isEmpty()) {
                    Text("Preencha gênero e horas nos jogos para ver aqui.", color = VaultColors.Muted, fontSize = 13.sp)
                } else {
                    val max = generos.maxOf { it.second }
                    generos.forEach { (g, h) -> LinhaBarra(g, "${Math.round(h)}h", (h / max).toFloat()) }
                }
            }
        }
    }
}

@Composable
private fun Cartao(titulo: String, conteudo: @Composable () -> Unit) {
    val forma = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().background(VaultColors.Surface, forma).border(1.dp, VaultColors.Line, forma).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(titulo, color = VaultColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        conteudo()
    }
}

@Composable
private fun Barra(fracao: Float) {
    val forma = RoundedCornerShape(50)
    Box(Modifier.fillMaxWidth().height(10.dp).clip(forma).background(VaultColors.Surface2)) {
        if (fracao > 0f) Box(Modifier.fillMaxWidth(fracao).height(10.dp).clip(forma).background(VaultColors.Red))
    }
}

@Composable
private fun LinhaBarra(rotulo: String, valor: String, fracao: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(rotulo, color = VaultColors.Muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(84.dp))
        Box(Modifier.weight(1f)) { Barra(fracao.coerceIn(0.03f, 1f)) }
        Text(valor, color = VaultColors.Text, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
    }
}
