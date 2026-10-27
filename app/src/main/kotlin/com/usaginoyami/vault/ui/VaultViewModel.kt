package com.usaginoyami.vault.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.usaginoyami.vault.data.Backup
import com.usaginoyami.vault.data.Covers
import com.usaginoyami.vault.data.Game
import com.usaginoyami.vault.data.JogoExcluido
import com.usaginoyami.vault.data.GameDb
import com.usaginoyami.vault.data.NovoJogo
import com.usaginoyami.vault.data.Status
import com.usaginoyami.vault.data.LimiteSteam
import com.usaginoyami.vault.data.Steam
import com.usaginoyami.vault.data.SteamGame
import com.usaginoyami.vault.logic.Filtro
import com.usaginoyami.vault.logic.MassaOpcoes
import com.usaginoyami.vault.logic.Ordem
import com.usaginoyami.vault.logic.candidatosMassa
import com.usaginoyami.vault.logic.DIAS_LIXEIRA
import com.usaginoyami.vault.logic.chaveNome
import com.usaginoyami.vault.logic.extrairAppId
import com.usaginoyami.vault.logic.filtrar
import com.usaginoyami.vault.logic.lerTags
import com.usaginoyami.vault.logic.semAcento
import com.usaginoyami.vault.logic.fmt
import com.usaginoyami.vault.logic.lancamentoBr
import com.usaginoyami.vault.logic.normalizarLancamento
import com.usaginoyami.vault.logic.numero
import com.usaginoyami.vault.logic.precisaCapa
import com.usaginoyami.vault.logic.precisaLancamento
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

sealed interface Tela {
    data object Biblioteca : Tela
    data class Edicao(val id: Long?) : Tela
    data object Steam : Tela
    data object Massa : Tela
    data object Lixeira : Tela
    data object Estatisticas : Tela
}

/** Campos da tela de edição, como texto (do jeito que o usuário digita). */
data class Form(
    /** Muda a cada jogo aberto; evita misturar respostas atrasadas de um jogo no outro. */
    val sessao: Long = 0,
    val id: Long? = null,
    val nome: String = "",
    val plataforma: String = "",
    val genero: String = "",
    val status: Status = Status.PENDENTE,
    val data: String = "",
    val horas: String = "",
    val nota: String = "",
    val video: String = "",
    val obs: String = "",
    /** Como o usuário digita: "2011" ou "18/04/2011". Guardado no banco em formato ISO. */
    val lancamento: String = "",
    /** ID da Steam como digitado (número ou link da loja). */
    val steamAppId: String = "",
    val tags: List<String> = emptyList(),
    /** Últimos valores válidos já gravados: valem enquanto o texto digitado estiver inválido. */
    val lancamentoGuardado: String? = null,
    val steamIdGuardado: Int? = null,
    val capaAtual: String? = null,
    val capaNova: String? = null,
    val removerCapa: Boolean = false,
    val buscandoCapa: Boolean = false,
) {
    val capaExibida: String? get() = capaNova ?: if (removerCapa) null else capaAtual

    val lancamentoInvalido: Boolean get() = lancamento.isNotBlank() && normalizarLancamento(lancamento) == null
    val steamIdInvalido: Boolean get() = steamAppId.isNotBlank() && extrairAppId(steamAppId) == null

    fun paraJogo(): Game = Game(
        id = id ?: 0,
        nome = nome.trim(),
        plataforma = plataforma.trim().ifEmpty { null },
        genero = genero.trim().ifEmpty { null },
        status = status,
        dataZerou = if (status == Status.ZERADO) data.trim().ifEmpty { null } else null,
        horas = numero(horas)?.coerceAtLeast(0.0),
        nota = numero(nota)?.coerceIn(0.0, 10.0),
        videoUrl = video.trim().ifEmpty { null },
        obs = obs.trim().ifEmpty { null },
        capa = capaExibida,
        lancamento = if (lancamento.isBlank()) null else (normalizarLancamento(lancamento) ?: lancamentoGuardado),
        steamAppId = if (steamAppId.isBlank()) null else (extrairAppId(steamAppId) ?: steamIdGuardado),
        tags = tags,
    )

    companion object {
        fun de(g: Game) = Form(
            id = g.id,
            nome = g.nome,
            plataforma = g.plataforma.orEmpty(),
            genero = g.genero.orEmpty(),
            status = g.status,
            data = g.dataZerou.orEmpty(),
            horas = g.horas?.let { fmt(it) }.orEmpty(),
            nota = g.nota?.let { fmt(it) }.orEmpty(),
            video = g.videoUrl.orEmpty(),
            obs = g.obs.orEmpty(),
            lancamento = lancamentoBr(g.lancamento),
            steamAppId = g.steamAppId?.toString().orEmpty(),
            tags = g.tags,
            lancamentoGuardado = g.lancamento,
            steamIdGuardado = g.steamAppId,
            capaAtual = g.capa,
        )
    }
}

class VaultViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication<Application>()
    private val banco = GameDb(File(app.filesDir, "jogos.db"))
    private val prefs = app.getSharedPreferences("vault", Context.MODE_PRIVATE)

    var jogos by mutableStateOf<List<Game>>(emptyList()); private set
    var carregado by mutableStateOf(false); private set
    var tela by mutableStateOf<Tela>(Tela.Biblioteca); private set

    /** Esconde estatísticas, busca e filtros para sobrar mais espaço para as capas. */
    var menuOculto by mutableStateOf(prefs.getBoolean("menu_oculto", false)); private set

    var busca by mutableStateOf("")
    var filtro by mutableStateOf(Filtro.TODOS)
    var ordem by mutableStateOf(Ordem.NOME)
    var tagFiltro by mutableStateOf<String?>(null)

    var lixeira by mutableStateOf<List<JogoExcluido>>(emptyList()); private set
    /** Último jogo mandado para a lixeira: enquanto não for null, o aviso oferece "Desfazer". */
    var ultimoExcluido by mutableStateOf<Long?>(null); private set
    /** Meta de jogos zerados por ano (0 = sem meta). */
    var metaAnual by mutableStateOf(prefs.getInt("meta_anual", 0)); private set

    var mensagem by mutableStateOf<String?>(null); private set
    var ocupado by mutableStateOf<String?>(null); private set

    var form by mutableStateOf(Form()); private set

    /** Ids dos jogos na ordem da lista quando a edição foi aberta (para arrastar para o próximo/anterior). */
    var navIds by mutableStateOf<List<Long>>(emptyList()); private set
    /** Texto de estado do salvamento automático ("Salvando…", "✓ Salvo"...). */
    var salvamento by mutableStateOf(""); private set
    private var alterado = false
    private var sessaoSeq = 0L
    private var debounceJob: Job? = null
    private val salvarMutex = Mutex()
    private val idsPorSessao = HashMap<Long, Long>()

    var steamChave by mutableStateOf(prefs.getString("steam_chave", "").orEmpty())
    var steamPerfil by mutableStateOf(prefs.getString("steam_perfil", "").orEmpty())
    var steamFiltro by mutableStateOf("")
    var steamStatus by mutableStateOf(""); private set
    var steamBuscando by mutableStateOf(false); private set
    var steamLista by mutableStateOf<List<SteamGame>>(emptyList()); private set
    var steamMarcados by mutableStateOf<Set<Int>>(emptySet()); private set
    var steamProgresso by mutableStateOf<String?>(null); private set

    var massaOpcoes by mutableStateOf(MassaOpcoes())
    var massaRodando by mutableStateOf(false); private set
    var massaProgresso by mutableStateOf(""); private set
    var massaFracao by mutableStateOf(0f); private set
    var massaResumo by mutableStateOf<String?>(null); private set
    var massaNaoAchados by mutableStateOf<List<String>>(emptyList()); private set
    private var massaJob: Job? = null

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // o que está na lixeira há mais de 30 dias some de vez
                banco.esvaziarLixeira(System.currentTimeMillis() - DIAS_LIXEIRA * 24L * 3600_000L)
                Covers.limparOrfas(ctx, banco.capasEmUso())
            }
            recarregar()
        }
    }

    private suspend fun recarregar() {
        jogos = withContext(Dispatchers.IO) { banco.todos() }
        carregado = true
    }

    fun limparMensagem() {
        mensagem = null
        ultimoExcluido = null
    }

    fun alternarMenu() {
        menuOculto = !menuOculto
        prefs.edit().putBoolean("menu_oculto", menuOculto).apply()
    }

    // ---------- navegação ----------

    private fun novaSessao(f: Form): Form = f.copy(sessao = ++sessaoSeq)

    fun novoJogo() {
        navIds = emptyList()
        form = novaSessao(Form())
        salvamento = ""
        alterado = false
        tela = Tela.Edicao(null)
    }

    fun editar(g: Game) {
        navIds = filtrar(jogos, filtro, busca, ordem, tagFiltro).map { it.id }
        form = novaSessao(Form.de(g))
        salvamento = ""
        alterado = false
        tela = Tela.Edicao(g.id)
    }

    fun abrirSteam() {
        steamStatus = ""
        tela = Tela.Steam
    }

    fun abrirMassa() {
        if (!massaRodando) {
            massaResumo = null
            massaNaoAchados = emptyList()
        }
        tela = Tela.Massa
    }

    fun voltar() {
        if (tela is Tela.Massa && massaRodando) return // pare o lote antes de sair
        if (tela is Tela.Edicao) {
            salvarPendente()
            // capa escolhida para um jogo que nunca chegou a ter nome: nada será salvo, então apaga o arquivo
            if (form.id == null && form.nome.isBlank()) form.capaNova?.let { Covers.apagar(ctx, it) }
        }
        tela = Tela.Biblioteca
    }

    fun proximo() = navegar(1)

    fun anterior() = navegar(-1)

    private fun navegar(passo: Int) {
        val atual = form.id ?: return
        val i = navIds.indexOf(atual)
        if (i < 0) return
        var j = i + passo
        while (j in navIds.indices && jogos.none { it.id == navIds[j] }) j += passo
        if (j !in navIds.indices) {
            mensagem = if (passo > 0) "Esse é o último jogo da lista." else "Esse é o primeiro jogo da lista."
            return
        }
        val alvoId = navIds[j]
        val sessao = form.sessao
        salvarPendente()
        viewModelScope.launch {
            salvarMutex.withLock { } // espera as gravações que já estavam na fila, para ler o jogo já atualizado
            if (form.sessao != sessao) return@launch
            val alvo = jogos.firstOrNull { it.id == alvoId } ?: return@launch
            form = novaSessao(Form.de(alvo))
            salvamento = ""
            alterado = false
            tela = Tela.Edicao(alvo.id)
        }
    }

    // ---------- edição (salva sozinha) ----------

    fun alterar(bloco: Form.() -> Form) {
        form = form.bloco()
        agendarSalvar()
    }

    fun mudarStatus(novo: Status) {
        val hoje = LocalDate.now().toString()
        form = form.copy(status = novo, data = if (novo == Status.ZERADO && form.data.isBlank()) hoje else form.data)
        agendarSalvar(150)
    }

    /** Aceita o número ou o link da loja; o link vira só o número. */
    fun mudarSteamId(texto: String) {
        form = form.copy(steamAppId = extrairAppId(texto)?.toString() ?: texto)
        agendarSalvar()
    }

    private fun agendarSalvar(atraso: Long = 700) {
        alterado = true
        if (form.nome.isNotBlank()) salvamento = "Salvando…"
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(atraso)
            alterado = false
            executarSalvar(form)
        }
    }

    /** Grava já o que estiver pendente (ao voltar, trocar de jogo ou sair do app). */
    fun salvarPendente() {
        debounceJob?.cancel()
        debounceJob = null
        if (alterado && tela is Tela.Edicao) {
            alterado = false
            val f = form
            viewModelScope.launch { executarSalvar(f) }
        }
    }

    private fun textoSalvo(f: Form): String = when {
        f.lancamentoInvalido -> "✓ Salvo (lançamento inválido: mantive o anterior)"
        f.steamIdInvalido -> "✓ Salvo (ID da Steam inválido: mantive o anterior)"
        else -> "✓ Salvo"
    }

    /** Uma gravação por vez (fila), sem poder ser cortada no meio: senão um jogo novo poderia ser inserido duas vezes. */
    private suspend fun executarSalvar(f0: Form) {
        withContext(NonCancellable) {
            salvarMutex.withLock {
                // na mesma tela, usa o que está agora no formulário (inclui o id de um jogo recém-criado)
                val f = if (form.sessao == f0.sessao) form else f0.copy(id = f0.id ?: idsPorSessao[f0.sessao])
                val naTela = { form.sessao == f.sessao }
                if (f.nome.isBlank()) {
                    if (naTela()) salvamento = if (f.id == null) "Digite o nome para criar o jogo" else "Nome obrigatório (não salvo)"
                    return@withLock
                }
                val jogo = f.paraJogo()
                val r = runCatching {
                    withContext(Dispatchers.IO) {
                        val id = banco.salvar(jogo)
                        if (f.capaAtual != null && f.capaAtual != jogo.capa) Covers.apagar(ctx, f.capaAtual)
                        id
                    }
                }
                r.onSuccess { id ->
                    idsPorSessao[f.sessao] = id
                    if (naTela()) {
                        val mesmaCapa = form.capaNova == f.capaNova && form.removerCapa == f.removerCapa
                        form = form.copy(
                            id = id,
                            capaAtual = jogo.capa,
                            capaNova = if (mesmaCapa) null else form.capaNova,
                            removerCapa = if (mesmaCapa) false else form.removerCapa,
                            lancamentoGuardado = jogo.lancamento,
                            steamIdGuardado = jogo.steamAppId,
                        )
                        salvamento = textoSalvo(form)
                    }
                    jogos = withContext(Dispatchers.IO) { banco.todos() }
                }.onFailure {
                    if (naTela()) salvamento = "Erro ao salvar: ${it.message}"
                }
            }
        }
    }

    fun escolherCapa(uri: Uri) {
        val sessao = form.sessao
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { Covers.salvarUri(ctx, uri) } }
            r.onSuccess { novo ->
                if (form.sessao != sessao) {
                    Covers.apagar(ctx, novo)
                    return@onSuccess
                }
                form.capaNova?.let { Covers.apagar(ctx, it) }
                form = form.copy(capaNova = novo, removerCapa = false)
                agendarSalvar(150)
            }.onFailure { mensagem = it.message ?: "Não consegui usar essa imagem." }
        }
    }

    fun tirarCapa() {
        form.capaNova?.let { Covers.apagar(ctx, it) }
        form = form.copy(capaNova = null, removerCapa = true)
        agendarSalvar(150)
    }

    private class AchadoSteam(val nome: String, val capa: String?, val appid: Int, val lancamento: String?)

    /** Põe capa/ID/data vindos da Steam no formulário (a data só sobrescreve a existente se [sobrescrever]). */
    private fun aplicarAchado(a: AchadoSteam, sobrescrever: Boolean) {
        if (a.capa != null) form.capaNova?.let { Covers.apagar(ctx, it) }
        form = form.copy(
            capaNova = a.capa ?: form.capaNova,
            removerCapa = if (a.capa != null) false else form.removerCapa,
            steamAppId = a.appid.toString(),
            lancamento = if (a.lancamento != null && (sobrescrever || form.lancamento.isBlank())) {
                lancamentoBr(a.lancamento)
            } else {
                form.lancamento
            },
        )
        agendarSalvar(150)
    }

    /** Procura pelo nome (palpite: pode pegar remake/remaster; para corrigir, use o ID da Steam). */
    fun buscarCapaNaSteam() {
        val nome = form.nome.trim()
        if (nome.isEmpty()) {
            mensagem = "Digite o nome do jogo primeiro."
            return
        }
        val sessao = form.sessao
        form = form.copy(buscandoCapa = true)
        viewModelScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val achado = Steam.buscarPorNome(nome) ?: return@withContext null
                    val arquivo = Steam.capa(ctx, achado.appid) ?: return@withContext null
                    val data = runCatching { Steam.lancamento(achado.appid) }.getOrNull()
                    AchadoSteam(achado.nome, arquivo, achado.appid, data)
                }
            }
            if (form.sessao != sessao) {
                r.getOrNull()?.capa?.let { Covers.apagar(ctx, it) }
                return@launch
            }
            r.onSuccess { a ->
                if (a == null) {
                    mensagem = "Não achei capa na Steam para esse nome."
                } else {
                    aplicarAchado(a, sobrescrever = false)
                    mensagem = "Capa de: ${a.nome} (ID ${a.appid}). Se não for o jogo certo, corrija o ID da Steam."
                }
            }.onFailure { mensagem = it.message ?: "Erro ao buscar a capa." }
            form = form.copy(buscandoCapa = false)
        }
    }

    /** Baixa capa e data de lançamento do ID da Steam digitado (substitui as atuais). */
    fun buscarPeloId() {
        val appid = extrairAppId(form.steamAppId)
        if (appid == null) {
            mensagem = "Digite o ID da Steam (ou cole o link da loja) primeiro."
            return
        }
        val sessao = form.sessao
        form = form.copy(buscandoCapa = true)
        viewModelScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val det = Steam.detalhes(appid) ?: return@withContext null
                    val arquivo = Steam.capa(ctx, appid)
                    AchadoSteam(det.nome, arquivo, appid, det.lancamento)
                }
            }
            if (form.sessao != sessao) {
                r.getOrNull()?.capa?.let { Covers.apagar(ctx, it) }
                return@launch
            }
            r.onSuccess { a ->
                if (a == null) {
                    mensagem = "Esse ID não existe na Steam."
                } else {
                    aplicarAchado(a, sobrescrever = true)
                    mensagem = "Dados de: ${a.nome}" + if (a.capa == null) " (a Steam não tem capa)" else ""
                }
            }.onFailure { mensagem = it.message ?: "Erro ao buscar pelo ID." }
            form = form.copy(buscandoCapa = false)
        }
    }

    fun excluir() {
        val f = form
        val id = f.id ?: return
        debounceJob?.cancel()
        alterado = false
        viewModelScope.launch {
            withContext(NonCancellable) {
                salvarMutex.withLock {
                    withContext(Dispatchers.IO) {
                        // capa nova ainda não gravada no jogo: apaga; a capa atual fica com o jogo na lixeira
                        if (f.capaNova != null) Covers.apagar(ctx, f.capaNova)
                        banco.paraLixeira(id, System.currentTimeMillis())
                    }
                }
            }
            tela = Tela.Biblioteca
            recarregar()
            ultimoExcluido = id // lido (e limpo) quando o aviso aparece, para oferecer "Desfazer"
            mensagem = "Jogo enviado para a lixeira."
        }
    }

    // ---------- lixeira ----------

    fun abrirLixeira() {
        viewModelScope.launch {
            lixeira = withContext(Dispatchers.IO) { banco.lixeira() }
            tela = Tela.Lixeira
        }
    }

    fun restaurar(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { banco.restaurar(id) }
            recarregar()
            lixeira = withContext(Dispatchers.IO) { banco.lixeira() }
            mensagem = "Jogo restaurado."
        }
    }

    fun apagarDeVez(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                banco.apagarDaLixeira(id)
                Covers.limparOrfas(ctx, banco.capasEmUso())
            }
            lixeira = withContext(Dispatchers.IO) { banco.lixeira() }
        }
    }

    fun esvaziarLixeira() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                banco.esvaziarLixeira()
                Covers.limparOrfas(ctx, banco.capasEmUso())
            }
            lixeira = emptyList()
            mensagem = "Lixeira esvaziada."
        }
    }

    // ---------- estatísticas ----------

    fun abrirEstatisticas() { tela = Tela.Estatisticas }

    fun definirMeta(n: Int) {
        metaAnual = n.coerceIn(0, 9999)
        prefs.edit().putInt("meta_anual", metaAnual).apply()
    }

    // ---------- etiquetas ----------

    fun adicionarTag(texto: String) {
        val nova = lerTags(texto).firstOrNull() ?: return
        if (form.tags.any { semAcento(it) == semAcento(nova) }) return
        alterar { copy(tags = tags + nova) }
    }

    fun removerTag(tag: String) = alterar { copy(tags = tags - tag) }

    // ---------- backup ----------

    fun exportar(uri: Uri) {
        viewModelScope.launch {
            ocupado = "Exportando..."
            val r = runCatching { withContext(Dispatchers.IO) { Backup.exportar(ctx, banco, uri) } }
            ocupado = null
            mensagem = r.fold(
                { "Backup salvo: ${jogos.size} jogos e $it capas." },
                { "Erro ao exportar: ${it.message}" },
            )
        }
    }

    fun importar(uri: Uri) {
        viewModelScope.launch {
            ocupado = "Importando..."
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val capas = Backup.importar(ctx, banco, uri)
                    Covers.limparOrfas(ctx, banco.capasEmUso())
                    capas
                }
            }
            r.onSuccess { capas ->
                recarregar()
                mensagem = "Importado: ${jogos.size} jogos e $capas capas."
            }.onFailure { mensagem = it.message ?: "Não consegui importar." }
            ocupado = null
        }
    }

    // ---------- Steam ----------

    fun steamBuscar() {
        val chave = steamChave.trim()
        val perfil = steamPerfil.trim()
        if (chave.isEmpty() || perfil.isEmpty()) {
            steamStatus = "Preencha a chave da API e o SteamID64 (ou link do perfil)."
            return
        }
        prefs.edit().putString("steam_chave", chave).putString("steam_perfil", perfil).apply()
        steamBuscando = true
        steamStatus = "Buscando..."
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { Steam.jogos(chave, perfil) } }
            steamBuscando = false
            r.onSuccess { todos ->
                val tenho = jogos.map { chaveNome(it.nome) }.toSet()
                steamLista = todos.filter { chaveNome(it.nome) !in tenho }.sortedBy { it.nome.lowercase() }
                steamMarcados = emptySet()
                steamFiltro = ""
                steamStatus = "${todos.size} jogos na Steam · ${steamLista.size} ainda não estão no seu banco"
            }.onFailure {
                steamLista = emptyList()
                steamStatus = "⚠ ${it.message}"
            }
        }
    }

    fun steamAlternar(appid: Int, marcado: Boolean) {
        steamMarcados = if (marcado) steamMarcados + appid else steamMarcados - appid
    }

    fun steamMarcarVisiveis(visiveis: List<SteamGame>, marcar: Boolean) {
        val ids = visiveis.map { it.appid }
        steamMarcados = if (marcar) steamMarcados + ids else steamMarcados - ids.toSet()
    }

    fun steamImportar() {
        val escolhidos = steamLista.filter { it.appid in steamMarcados }
        if (escolhidos.isEmpty() || steamProgresso != null) return
        viewModelScope.launch {
            steamProgresso = "Baixando capas 0/${escolhidos.size}"
            val r = runCatching {
                val novos = withContext(Dispatchers.IO) {
                    val limite = Semaphore(6)
                    val feitos = AtomicInteger(0)
                    coroutineScope {
                        escolhidos.map { g ->
                            async {
                                limite.withPermit {
                                    val capa = runCatching { Steam.capa(ctx, g.appid) }.getOrNull()
                                    val n = feitos.incrementAndGet()
                                    withContext(Dispatchers.Main) { steamProgresso = "Baixando capas $n/${escolhidos.size}" }
                                    NovoJogo(g.nome, g.horas, capa, g.appid)
                                }
                            }
                        }.awaitAll()
                    }
                }
                withContext(Dispatchers.IO) { banco.inserirSteam(novos) }
            }
            steamProgresso = null
            r.onSuccess {
                steamLista = steamLista.filter { it.appid !in steamMarcados }
                val qtd = escolhidos.size
                steamMarcados = emptySet()
                recarregar()
                tela = Tela.Biblioteca
                mensagem = "$qtd jogos importados da Steam."
            }.onFailure { mensagem = "Erro ao importar: ${it.message}" }
        }
    }

    // ---------- capas (e datas) em massa ----------

    private class ResultadoMassa(val capa: Boolean, val data: Boolean)

    /** Trabalha num jogo; null = não achei na Steam (ou a Steam não tem capa/data). Bloqueante. */
    private fun processarMassa(g: Game, o: MassaOpcoes): ResultadoMassa? {
        val appid = g.steamAppId ?: Steam.buscarPorNome(g.nome, estrito = true)?.appid ?: return null
        val capa = if (precisaCapa(g, o)) Steam.capa(ctx, appid) else null
        val data = if (precisaLancamento(g, o)) Steam.lancamento(appid) else null
        if (capa == null && data == null) return null
        val antiga = banco.atualizarSteam(g.id, appid, capa, data)
        if (capa != null && antiga != null && antiga != capa) Covers.apagar(ctx, antiga)
        return ResultadoMassa(capa != null, data != null)
    }

    fun massaIniciar() {
        if (massaRodando) return
        val o = massaOpcoes
        val lista = candidatosMassa(jogos, o)
        if (lista.isEmpty() || !(o.capas || o.lancamento)) return
        massaRodando = true
        massaResumo = null
        massaNaoAchados = emptyList()
        massaFracao = 0f
        massaJob = viewModelScope.launch {
            var capas = 0
            var datas = 0
            var falhas = 0
            var seguidas = 0
            var motivoParada: String? = null
            var terminou = false
            val naoAchados = ArrayList<String>()
            try {
                for ((i, g) in lista.withIndex()) {
                    ensureActive()
                    massaProgresso = "${i + 1}/${lista.size} · ${g.nome}"
                    massaFracao = i.toFloat() / lista.size
                    var tentativas = 0
                    while (true) {
                        try {
                            val r = withContext(Dispatchers.IO) { processarMassa(g, o) }
                            seguidas = 0
                            if (r == null) naoAchados += g.nome else {
                                if (r.capa) capas++
                                if (r.data) datas++
                            }
                            break
                        } catch (e: LimiteSteam) {
                            if (tentativas++ >= 1) {
                                motivoParada = e.message
                                break
                            }
                            massaProgresso = "A Steam pediu para esperar. Retomando em 1 minuto..."
                            delay(60_000)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            falhas++
                            seguidas++
                            if (seguidas >= 5) motivoParada = e.message ?: "Sem conexão."
                            break
                        }
                    }
                    if (motivoParada != null) break
                    if (i % 5 == 4) recarregar()
                }
                terminou = motivoParada == null
            } finally {
                withContext(NonCancellable) { recarregar() }
                massaNaoAchados = naoAchados
                massaRodando = false
                massaFracao = 1f
                val partes = ArrayList<String>()
                partes += "$capas capas baixadas"
                if (o.lancamento) partes += "$datas datas de lançamento"
                if (naoAchados.isNotEmpty()) partes += "${naoAchados.size} não encontrados"
                if (falhas > 0) partes += "$falhas com erro"
                massaResumo = (if (terminou) "Concluído: " else if (motivoParada != null) "Parou (${motivoParada}): " else "Parado: ") +
                    partes.joinToString(", ") + "."
            }
        }
    }

    fun massaParar() {
        massaJob?.cancel()
    }
}
