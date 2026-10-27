package com.usaginoyami.vault.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.usaginoyami.vault.data.Covers
import com.usaginoyami.vault.data.Game
import com.usaginoyami.vault.data.Status
import com.usaginoyami.vault.logic.Filtro
import com.usaginoyami.vault.logic.Ordem
import com.usaginoyami.vault.logic.anoDe
import com.usaginoyami.vault.logic.estatisticas
import com.usaginoyami.vault.logic.filtrar
import com.usaginoyami.vault.logic.fmt
import com.usaginoyami.vault.logic.todasTags
import java.io.File

@Composable
fun BibliotecaScreen(vm: VaultViewModel, onExportar: () -> Unit, onImportar: () -> Unit) {
    val ctx = LocalContext.current
    val capasDir = remember { Covers.dir(ctx) }
    val visiveis = remember(vm.jogos, vm.filtro, vm.busca, vm.ordem, vm.tagFiltro) {
        filtrar(vm.jogos, vm.filtro, vm.busca, vm.ordem, vm.tagFiltro)
    }
    val stats = remember(vm.jogos) { estatisticas(vm.jogos) }
    val tags = remember(vm.jogos) { todasTags(vm.jogos) }
    // se a etiqueta filtrada deixou de existir (jogo editado/excluído), volta para "todas"
    LaunchedEffect(tags) { if (vm.tagFiltro != null && tags.none { it.equals(vm.tagFiltro, true) }) vm.tagFiltro = null }
    var menu by remember { mutableStateOf(false) }
    var menuOrdem by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildAnnotatedString {
                        append("Usagi ")
                        withStyle(SpanStyle(color = VaultColors.RedBright)) { append("Vault") }
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = VaultColors.Text,
                    modifier = Modifier.weight(1f),
                )
                if (vm.menuOculto) {
                    Text("${visiveis.size} jogos", color = VaultColors.Muted, fontSize = 12.sp)
                } else {
                    OutlinedButton(onClick = { vm.abrirSteam() }) { Text("⬇ Steam") }
                }
                TextButton(onClick = { vm.alternarMenu() }) {
                    Text(if (vm.menuOculto) "▼" else "▲", fontSize = 16.sp, color = VaultColors.Text)
                }
                Box {
                    TextButton(onClick = { menu = true }) { Text("⋮", fontSize = 22.sp, color = VaultColors.Text) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Importar da Steam") },
                            onClick = { menu = false; vm.abrirSteam() },
                        )
                        DropdownMenuItem(
                            text = { Text("Baixar capas em massa") },
                            onClick = { menu = false; vm.abrirMassa() },
                        )
                        DropdownMenuItem(
                            text = { Text("Estatísticas e meta") },
                            onClick = { menu = false; vm.abrirEstatisticas() },
                        )
                        DropdownMenuItem(
                            text = { Text("Lixeira") },
                            onClick = { menu = false; vm.abrirLixeira() },
                        )
                        DropdownMenuItem(
                            text = { Text("Exportar backup (.zip)") },
                            onClick = { menu = false; onExportar() },
                        )
                        DropdownMenuItem(
                            text = { Text("Importar backup (.zip ou .db)") },
                            onClick = { menu = false; onImportar() },
                        )
                    }
                }
            }

            AnimatedVisibility(visible = !vm.menuOculto) {
                Column {
            val itensStats = listOf(
                stats.total.toString() to "jogos",
                stats.zerados.toString() to "zerados",
                stats.semFim.toString() to "sem fim ∞",
                stats.dropados.toString() to "dropados",
                stats.pendentes.toString() to "pendentes",
                "${stats.horas}h" to "jogadas",
                (stats.notaMedia?.let { fmt(it) } ?: "—") to "nota média",
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(itensStats) { (valor, rotulo) -> StatChip(valor, rotulo) }
            }

            OutlinedTextField(
                value = vm.busca,
                onValueChange = { vm.busca = it },
                placeholder = { Text("Buscar nome, plataforma, gênero", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                trailingIcon = {
                    if (vm.busca.isNotEmpty()) {
                        Text(
                            "✕",
                            color = VaultColors.Muted,
                            modifier = Modifier.clickable { vm.busca = "" }.padding(12.dp),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Filtro.entries.toList()) { f ->
                    FilterChip(
                        selected = vm.filtro == f,
                        onClick = { vm.filtro = f },
                        label = { Text(f.rotulo) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultColors.Red,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }

            if (tags.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tags) { t ->
                        FilterChip(
                            selected = vm.tagFiltro.equals(t, ignoreCase = true),
                            onClick = { vm.tagFiltro = if (vm.tagFiltro.equals(t, ignoreCase = true)) null else t },
                            label = { Text("#$t") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VaultColors.Red,
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${visiveis.size} jogos", color = VaultColors.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { menuOrdem = true }) {
                        Text("Ordem: ${vm.ordem.rotulo} ▾", color = VaultColors.Muted, fontSize = 12.sp)
                    }
                    DropdownMenu(expanded = menuOrdem, onDismissRequest = { menuOrdem = false }) {
                        Ordem.entries.forEach { o ->
                            DropdownMenuItem(
                                text = { Text(o.rotulo) },
                                onClick = { vm.ordem = o; menuOrdem = false },
                            )
                        }
                    }
                }
            }

                }
            }

            when {
                !vm.carregado -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VaultColors.Red)
                }
                visiveis.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (vm.jogos.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Text("Biblioteca vazia", color = VaultColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                "Importe o backup que veio do PC, traga seus jogos da Steam ou toque no + para adicionar um.",
                                color = VaultColors.Muted,
                                textAlign = TextAlign.Center,
                            )
                            Button(onClick = onImportar) { Text("Importar backup do PC") }
                        }
                    } else {
                        Text("Nada encontrado.", color = VaultColors.Muted)
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visiveis, key = { it.id }) { g ->
                        JogoCard(g, capasDir) { vm.editar(g) }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { vm.novoJogo() },
            containerColor = VaultColors.Red,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
        ) {
            Text("+", fontSize = 28.sp)
        }
    }
}

@Composable
private fun JogoCard(g: Game, capasDir: File, onClick: () -> Unit) {
    val forma = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .clip(forma)
            .background(VaultColors.Surface)
            .border(1.dp, VaultColors.Line, forma)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(VaultColors.Surface2)) {
            Text(
                g.nome.take(1).uppercase(),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = VaultColors.Red.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center),
            )
            if (g.capa != null) {
                AsyncImage(
                    model = File(capasDir, g.capa),
                    contentDescription = g.nome,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val selo = Modifier.align(Alignment.TopEnd).padding(6.dp)
            when (g.status) {
                Status.ZERADO -> Selo("✔ ZERADO", VaultColors.Ok, VaultColors.OnOk, selo)
                Status.SEM_FIM -> Selo("∞ SEM FIM", VaultColors.Infinito, Color.White, selo)
                Status.DROPADO -> Selo("✘ DROPADO", VaultColors.Drop, VaultColors.OnDrop, selo)
                Status.PENDENTE -> Unit
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                g.nome,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = VaultColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(g.plataforma ?: "—", anoDe(g.lancamento), g.nota?.let { "★ ${fmt(it)}" })
                .joinToString(" · ")
            Text(sub, fontSize = 11.sp, color = VaultColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (g.tags.isNotEmpty()) {
                Text(
                    g.tags.joinToString(" ") { "#$it" },
                    fontSize = 10.sp, color = VaultColors.RedBright, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
