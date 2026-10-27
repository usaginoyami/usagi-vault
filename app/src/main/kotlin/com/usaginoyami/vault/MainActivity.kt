package com.usaginoyami.vault

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.usaginoyami.vault.ui.BibliotecaScreen
import com.usaginoyami.vault.ui.CamadaOcupado
import com.usaginoyami.vault.ui.EdicaoScreen
import com.usaginoyami.vault.ui.MassaScreen
import com.usaginoyami.vault.ui.SteamScreen
import com.usaginoyami.vault.ui.Tela
import com.usaginoyami.vault.ui.VaultColors
import com.usaginoyami.vault.ui.VaultTheme
import com.usaginoyami.vault.ui.VaultViewModel
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm: VaultViewModel by viewModels()

    override fun onStop() {
        super.onStop()
        vm.salvarPendente()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent { VaultTheme { VaultApp(vm) } }
    }
}

@Composable
fun VaultApp(vm: VaultViewModel) {
    val snackbar = remember { SnackbarHostState() }
    var confirmarImportar by remember { mutableStateOf(false) }

    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportar(uri)
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importar(uri)
    }

    LaunchedEffect(vm.mensagem) {
        val m = vm.mensagem
        if (m != null) {
            val excluido = vm.ultimoExcluido
            vm.limparMensagem()
            if (excluido != null) {
                val r = snackbar.showSnackbar(m, actionLabel = "Desfazer", duration = SnackbarDuration.Long)
                if (r == SnackbarResult.ActionPerformed) vm.restaurar(excluido)
            } else {
                snackbar.showSnackbar(m)
            }
        }
    }

    BackHandler(enabled = vm.tela != Tela.Biblioteca) { vm.voltar() }

    Box(Modifier.fillMaxSize().background(VaultColors.Bg)) {
        when (vm.tela) {
            Tela.Biblioteca -> BibliotecaScreen(
                vm = vm,
                onExportar = { exportar.launch("usagi-vault-${LocalDate.now()}.zip") },
                onImportar = { confirmarImportar = true },
            )
            is Tela.Edicao -> EdicaoScreen(vm)
            Tela.Steam -> SteamScreen(vm)
            Tela.Massa -> MassaScreen(vm)
            Tela.Lixeira -> LixeiraScreen(vm)
            Tela.Estatisticas -> EstatisticasScreen(vm)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        vm.ocupado?.let { CamadaOcupado(it) }
    }

    if (confirmarImportar) {
        AlertDialog(
            onDismissRequest = { confirmarImportar = false },
            title = { Text("Importar backup?") },
            text = {
                Text(
                    "Isso substitui toda a biblioteca atual pelo conteúdo do arquivo " +
                        "(.zip com jogos.db e capas, ou só o jogos.db). Uma cópia da biblioteca de agora fica guardada no app."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarImportar = false
                    importar.launch(arrayOf("*/*"))
                }) { Text("Escolher arquivo") }
            },
            dismissButton = { TextButton(onClick = { confirmarImportar = false }) { Text("Cancelar") } },
        )
    }
}
