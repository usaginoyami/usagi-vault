package com.usaginoyami.vault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usaginoyami.vault.logic.fmt
import com.usaginoyami.vault.logic.semAcento

@Composable
fun SteamScreen(vm: VaultViewModel) {
    val uri = LocalUriHandler.current
    val q = semAcento(vm.steamFiltro.trim())
    val visiveis = remember(vm.steamLista, vm.steamFiltro) {
        if (q.isEmpty()) vm.steamLista else vm.steamLista.filter { semAcento(it.nome).contains(q) }
    }
    val qtd = vm.steamMarcados.size
    val importando = vm.steamProgresso != null

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.voltar() }, enabled = !importando) { Text("✕ Fechar", color = VaultColors.Muted) }
            Text(
                "Importar da Steam",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = VaultColors.Text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text("", Modifier.padding(horizontal = 40.dp))
        }

        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = vm.steamChave,
                onValueChange = { vm.steamChave = it },
                label = { Text("Chave da API da Steam") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { uri.openUri("https://steamcommunity.com/dev/apikey") }) {
                Text("Pegar minha chave (steamcommunity.com/dev/apikey)", fontSize = 12.sp)
            }
            OutlinedTextField(
                value = vm.steamPerfil,
                onValueChange = { vm.steamPerfil = it },
                label = { Text("SteamID64 ou link do seu perfil") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Perfil e \"detalhes dos jogos\" precisam estar públicos na hora de importar. " +
                    "A chave fica só neste celular.",
                color = VaultColors.Muted,
                fontSize = 12.sp,
            )
            Button(
                onClick = { vm.steamBuscar() },
                enabled = !vm.steamBuscando && !importando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (vm.steamBuscando) "Buscando..." else "Buscar meus jogos") }
            if (vm.steamStatus.isNotEmpty()) {
                Text(vm.steamStatus, color = VaultColors.Muted, fontSize = 13.sp)
            }
        }

        if (vm.steamLista.isNotEmpty()) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                OutlinedTextField(
                    value = vm.steamFiltro,
                    onValueChange = { vm.steamFiltro = it },
                    placeholder = { Text("Filtrar...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.steamMarcarVisiveis(visiveis, true) }, enabled = !importando) {
                        Text("Marcar visíveis", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { vm.steamMarcarVisiveis(visiveis, false) }, enabled = !importando) {
                        Text("Desmarcar visíveis", fontSize = 12.sp)
                    }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(visiveis, key = { it.appid }) { g ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = g.appid in vm.steamMarcados,
                                onCheckedChange = { vm.steamAlternar(g.appid, it) },
                                enabled = !importando,
                                colors = CheckboxDefaults.colors(checkedColor = VaultColors.Red),
                            )
                            Text(
                                g.nome,
                                color = VaultColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${fmt(g.horas)}h", color = VaultColors.Muted, fontSize = 12.sp)
                        }
                    }
                }
                Button(
                    onClick = { vm.steamImportar() },
                    enabled = qtd > 0 && !importando,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(vm.steamProgresso ?: "Importar $qtd jogo(s)")
                }
            }
        }
    }
}
