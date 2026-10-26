package com.usaginoyami.vault.ui

import android.app.DatePickerDialog
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.usaginoyami.vault.data.Covers
import com.usaginoyami.vault.data.Status
import com.usaginoyami.vault.logic.dataBr
import com.usaginoyami.vault.logic.partesData
import java.io.File
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale

@Composable
fun EdicaoScreen(vm: VaultViewModel) {
    val f = vm.form
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val limiteArraste = with(androidx.compose.ui.platform.LocalDensity.current) { 90.dp.toPx() }
    val posicao = vm.navIds.indexOf(f.id ?: -1L)
    val rolagem = remember(f.sessao) { ScrollState(0) }
    val capasDir = remember { Covers.dir(ctx) }
    var confirmarExcluir by remember { mutableStateOf(false) }
    val escolherImagem = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.escolherCapa(uri)
    }
    val abrirGaleria = {
        escolherImagem.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val plataformas = remember(vm.jogos) {
        vm.jogos.mapNotNull { it.plataforma }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(8).map { it.key }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.voltar() }) { Text("← Voltar", color = VaultColors.Muted) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (f.id == null) "Novo jogo" else "Editar jogo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = VaultColors.Text,
                )
                val sub = listOfNotNull(
                    if (posicao >= 0) "${posicao + 1}/${vm.navIds.size}" else null,
                    vm.salvamento.ifEmpty { null },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        fontSize = 11.sp,
                        color = if (vm.salvamento.startsWith("✓")) VaultColors.Ok else VaultColors.Muted,
                    )
                }
            }
            if (posicao >= 0) {
                TextButton(onClick = { vm.anterior() }, enabled = posicao > 0) { Text("‹", fontSize = 22.sp) }
                TextButton(onClick = { vm.proximo() }, enabled = posicao < vm.navIds.size - 1) { Text("›", fontSize = 22.sp) }
            }
        }

        Column(
            Modifier.weight(1f)
                .pointerInput(f.sessao) {
                    var soma = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { soma = 0f },
                        onDragEnd = {
                            if (soma <= -limiteArraste) vm.proximo() else if (soma >= limiteArraste) vm.anterior()
                            soma = 0f
                        },
                        onDragCancel = { soma = 0f },
                    ) { _, dx -> soma += dx }
                }
                .verticalScroll(rolagem).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val forma = RoundedCornerShape(12.dp)
                Box(
                    Modifier
                        .width(120.dp)
                        .aspectRatio(2f / 3f)
                        .clip(forma)
                        .background(VaultColors.Surface2)
                        .border(1.dp, VaultColors.Line, forma)
                        .clickable { abrirGaleria() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Toque para\nescolher a capa",
                        color = VaultColors.Muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    val capa = f.capaExibida
                    if (capa != null) {
                        AsyncImage(
                            model = File(capasDir, capa),
                            contentDescription = "Capa",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { abrirGaleria() }) { Text("Escolher da galeria") }
                    OutlinedButton(onClick = { vm.buscarCapaNaSteam() }, enabled = !f.buscandoCapa) {
                        Text(if (f.buscandoCapa) "Buscando..." else "Buscar na Steam")
                    }
                    TextButton(onClick = { vm.tirarCapa() }, enabled = f.capaExibida != null) { Text("Tirar capa") }
                }
            }

            Campo("Nome", f.nome) { vm.alterar { copy(nome = it) } }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Campo("Plataforma", f.plataforma, Modifier.weight(1f)) { vm.alterar { copy(plataforma = it) } }
                Campo("Gênero", f.genero, Modifier.weight(1f)) { vm.alterar { copy(genero = it) } }
            }
            if (plataformas.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    plataformas.forEach { p ->
                        AssistChip(onClick = { vm.alterar { copy(plataforma = p) } }, label = { Text(p) })
                    }
                }
            }

            Text("Status", color = VaultColors.Muted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Status.entries.forEach { s ->
                    FilterChip(
                        selected = f.status == s,
                        onClick = { vm.mudarStatus(s) },
                        label = { Text(s.rotulo) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultColors.Red,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }

            if (f.status == Status.ZERADO) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        escolherData(ctx, f.data) { nova -> vm.alterar { copy(data = nova) } }
                    }) {
                        Text(if (f.data.isBlank()) "Data que zerou" else "Zerou em ${dataBr(f.data)}")
                    }
                    TextButton(onClick = { vm.alterar { copy(data = LocalDate.now().toString()) } }) { Text("Hoje") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Campo("Horas", f.horas, Modifier.weight(1f), KeyboardType.Decimal) { vm.alterar { copy(horas = it) } }
                Campo("Nota (0-10)", f.nota, Modifier.weight(1f), KeyboardType.Decimal) { vm.alterar { copy(nota = it) } }
            }
            Campo(
                "Lançamento (ex: 2011 ou 18/04/2011)", f.lancamento,
                erro = if (f.lancamentoInvalido) "Use 2011 ou 18/04/2011" else null,
            ) { vm.alterar { copy(lancamento = it) } }
            Campo(
                "ID da Steam (ou cole o link da loja)", f.steamAppId, teclado = KeyboardType.Uri,
                erro = if (f.steamIdInvalido) "Digite só o número ou cole o link da loja" else null,
            ) { vm.mudarSteamId(it) }
            Text(
                "Se a capa ou a data vieram de outro jogo (remake, remaster...), cole aqui o ID certo: " +
                    "é o número no link da loja (store.steampowered.com/app/NÚMERO).",
                color = VaultColors.Muted, fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.buscarPeloId() },
                    enabled = !f.buscandoCapa && f.steamAppId.isNotBlank() && !f.steamIdInvalido,
                ) { Text(if (f.buscandoCapa) "Buscando..." else "Baixar capa e data por este ID") }
            }
            val idSteam = com.usaginoyami.vault.logic.extrairAppId(f.steamAppId)
            TextButton(onClick = {
                val url = if (idSteam != null) {
                    "https://store.steampowered.com/app/$idSteam"
                } else {
                    "https://store.steampowered.com/search/?term=" + URLEncoder.encode(f.nome.trim(), "UTF-8")
                }
                runCatching { uriHandler.openUri(url) }
            }) { Text(if (idSteam != null) "Ver na Steam" else "Procurar na Steam") }
            Campo("Vídeo no canal", f.video, teclado = KeyboardType.Uri) { vm.alterar { copy(video = it) } }
            Campo("Observações", f.obs, linhas = 3) { vm.alterar { copy(obs = it) } }

            Text(
                "Arraste para o lado para trocar de jogo · tudo é salvo sozinho",
                color = VaultColors.Muted, fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (f.id != null) {
                TextButton(
                    onClick = { confirmarExcluir = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = VaultColors.Drop),
                ) { Text("Excluir jogo") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmarExcluir) {
        AlertDialog(
            onDismissRequest = { confirmarExcluir = false },
            title = { Text("Excluir este jogo?") },
            text = { Text("\"${f.nome}\" será apagado da biblioteca.") },
            confirmButton = {
                TextButton(onClick = { confirmarExcluir = false; vm.excluir() }) {
                    Text("Excluir", color = VaultColors.Drop)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarExcluir = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Campo(
    rotulo: String,
    valor: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    teclado: KeyboardType = KeyboardType.Text,
    linhas: Int = 1,
    erro: String? = null,
    aoMudar: (String) -> Unit,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        singleLine = linhas == 1,
        minLines = linhas,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        isError = erro != null,
        supportingText = { if (erro != null) Text(erro) },
        modifier = modifier,
    )
}

private fun escolherData(ctx: Context, atual: String, aoEscolher: (String) -> Unit) {
    val hoje = LocalDate.now()
    val (ano, mes, dia) = partesData(atual) ?: Triple(hoje.year, hoje.monthValue, hoje.dayOfMonth)
    DatePickerDialog(
        ctx,
        { _, a, m, d -> aoEscolher(String.format(Locale.US, "%04d-%02d-%02d", a, m + 1, d)) },
        ano,
        mes - 1,
        dia,
    ).show()
}
