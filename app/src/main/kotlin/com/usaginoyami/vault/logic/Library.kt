package com.usaginoyami.vault.logic

import com.usaginoyami.vault.data.Game
import com.usaginoyami.vault.data.Status
import java.text.Collator
import java.time.LocalDate
import java.time.YearMonth
import java.text.Normalizer
import java.util.Locale

enum class Filtro(val rotulo: String) {
    TODOS("Todos"),
    ZERADOS("Zerados"),
    PENDENTES("Pendentes"),
    SEM_FIM("Sem fim ∞"),
    DROPADOS("Dropei"),
}

enum class Ordem(val rotulo: String) {
    NOME("Nome"),
    NOTA("Nota"),
    HORAS("Horas"),
    ZEROU("Zerado em"),
    LANC_ASC("Lançamento (antigos 1º)"),
    LANC_DESC("Lançamento (novos 1º)"),
}

data class Stats(
    val total: Int,
    val zerados: Int,
    val semFim: Int,
    val dropados: Int,
    val pendentes: Int,
    val horas: Long,
    val notaMedia: Double?,
)

private val REGEX_MARCAS = Regex("\\p{M}+")

/** Minúsculas e sem acento, para a busca achar "pokemon" em "Pokémon". */
fun semAcento(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(REGEX_MARCAS, "").lowercase()

/** Só letras e números, sem acento: "Hades™" e "hades" viram a mesma chave. */
fun chaveNome(s: String): String = semAcento(s).filter { it.isLetterOrDigit() }

private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))

fun filtrar(jogos: List<Game>, filtro: Filtro, busca: String, ordem: Ordem, tag: String? = null): List<Game> {
    val q = semAcento(busca.trim())
    val base = jogos.filter { j ->
        val okFiltro = when (filtro) {
            Filtro.TODOS -> true
            Filtro.ZERADOS -> j.status == Status.ZERADO
            Filtro.PENDENTES -> j.status == Status.PENDENTE
            Filtro.SEM_FIM -> j.status == Status.SEM_FIM
            Filtro.DROPADOS -> j.status == Status.DROPADO
        }
        okFiltro && (tag == null || j.tags.any { it.equals(tag, ignoreCase = true) }) && (q.isEmpty() ||
            semAcento("${j.nome} ${j.plataforma.orEmpty()} ${j.genero.orEmpty()} ${j.tags.joinToString(" ")}").contains(q))
    }
    val porNome = Comparator<Game> { a, b -> collator.compare(a.nome, b.nome) }
    return when (ordem) {
        Ordem.NOME -> base.sortedWith(porNome)
        Ordem.NOTA -> base.sortedWith(compareByDescending<Game> { it.nota ?: -1.0 }.then(porNome))
        Ordem.HORAS -> base.sortedWith(compareByDescending<Game> { it.horas ?: -1.0 }.then(porNome))
        Ordem.ZEROU -> base.sortedWith(compareByDescending<Game> { it.dataZerou.orEmpty() }.then(porNome))
        // quem não tem data de lançamento vai sempre para o fim
        Ordem.LANC_ASC -> base.sortedWith(
            compareBy<Game> { it.lancamento == null }.thenBy { it.lancamento.orEmpty() }.then(porNome)
        )
        Ordem.LANC_DESC -> base.sortedWith(
            compareBy<Game> { it.lancamento == null }.thenByDescending { it.lancamento.orEmpty() }.then(porNome)
        )
    }
}

fun estatisticas(jogos: List<Game>): Stats {
    val notas = jogos.mapNotNull { it.nota }
    return Stats(
        total = jogos.size,
        zerados = jogos.count { it.status == Status.ZERADO },
        semFim = jogos.count { it.status == Status.SEM_FIM },
        dropados = jogos.count { it.status == Status.DROPADO },
        pendentes = jogos.count { it.status == Status.PENDENTE },
        horas = Math.round(jogos.sumOf { it.horas ?: 0.0 }),
        notaMedia = if (notas.isEmpty()) null else notas.average(),
    )
}

/** 7.0 -> "7", 7.5 -> "7,5" (vírgula, como no Brasil). */
fun fmt(d: Double): String {
    val r = Math.round(d * 10) / 10.0
    return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString().replace('.', ',')
}

/** Aceita "7,5" e "7.5"; vazio ou lixo vira null. */
fun numero(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** "2024-03-15" -> "15/03/2024". */
fun dataBr(iso: String?): String {
    val p = iso?.split("-")
    return if (p != null && p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else iso.orEmpty()
}

/** "2024-03-15" -> (2024, 3, 15) ou null se não for uma data válida. */
fun partesData(iso: String?): Triple<Int, Int, Int>? {
    val p = iso?.split("-")?.mapNotNull { it.toIntOrNull() }
    return if (p != null && p.size == 3 && p[1] in 1..12 && p[2] in 1..31) Triple(p[0], p[1], p[2]) else null
}

/**
 * Escolhe qual resultado da busca da Steam é o jogo procurado.
 * Igual ao nome (sem acento/símbolos) > começa igual > primeiro da lista. -1 se a lista é vazia.
 */
fun melhorResultado(consulta: String, nomes: List<String>, estrito: Boolean = false): Int {
    if (nomes.isEmpty()) return -1
    val c = chaveNome(consulta)
    val exato = nomes.indexOfFirst { chaveNome(it) == c }
    if (exato >= 0) return exato
    if (estrito) {
        // modo "em massa": só aceita igual, ou igual + subtítulo ("The Witcher 3" -> "The Witcher 3: Wild Hunt").
        // Evita pegar a sequência errada ("Hades" -> "Hades II") e pôr capa trocada.
        val q = semAcento(consulta.trim())
        return nomes.indexOfFirst {
            val n = semAcento(it.trim())
            q.isNotEmpty() && n.startsWith(q) && n.drop(q.length).trimStart().firstOrNull()?.let { c -> c in SEPARADORES_SUBTITULO } == true
        }
    }
    val parecido = nomes.indexOfFirst {
        val k = chaveNome(it)
        c.isNotEmpty() && k.isNotEmpty() && (k.startsWith(c) || c.startsWith(k))
    }
    return if (parecido >= 0) parecido else 0
}

private val SEPARADORES_SUBTITULO = setOf(':', '-', '–', '—')

private val MESES = mapOf(
    "jan" to 1, "feb" to 2, "fev" to 2, "mar" to 3, "apr" to 4, "abr" to 4, "may" to 5, "mai" to 5,
    "jun" to 6, "jul" to 7, "aug" to 8, "ago" to 8, "sep" to 9, "set" to 9, "oct" to 10, "out" to 10,
    "nov" to 11, "dec" to 12, "dez" to 12,
)

/**
 * Entende a data de lançamento como a Steam escreve ("18 Apr, 2011", "Apr 2011", "2011", "18 de abr. de 2011")
 * e devolve "2011-04-18", "2011-04" ou "2011". Sem ano ("Coming soon", "To be announced") -> null.
 */
fun parseDataSteam(texto: String): String? {
    val tokens = texto.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    val ano = tokens.firstOrNull { it.length == 4 && it.all(Char::isDigit) && it.toInt() in 1970..2100 } ?: return null
    val mes = tokens.firstNotNullOfOrNull { t -> if (t.length >= 3 && t.all(Char::isLetter)) MESES[t.take(3).lowercase()] else null }
        ?: return ano
    val dia = tokens.firstOrNull { it.length <= 2 && it.all(Char::isDigit) && it.toInt() in 1..31 }?.toInt()
    val mm = mes.toString().padStart(2, '0')
    return if (dia == null) "$ano-$mm" else "$ano-$mm-${dia.toString().padStart(2, '0')}"
}

/**
 * Aceita o que o usuário digita: "2011", "04/2011", "18/04/2011" ou "2011-04-18" e devolve o formato guardado
 * ("2011", "2011-04", "2011-04-18"). Texto vazio ou inválido -> null.
 */
fun normalizarLancamento(entrada: String): String? {
    val t = entrada.trim()
    Regex("^(\\d{4})$").matchEntire(t)?.let { return validarAno(it.groupValues[1]) }
    Regex("^(\\d{4})-(\\d{2})$").matchEntire(t)?.let { return montar(it.groupValues[1], it.groupValues[2], null) }
    Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(t)?.let { return montar(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
    Regex("^(\\d{1,2})/(\\d{4})$").matchEntire(t)?.let { return montar(it.groupValues[2], it.groupValues[1].padStart(2, '0'), null) }
    Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$").matchEntire(t)?.let {
        return montar(it.groupValues[3], it.groupValues[2].padStart(2, '0'), it.groupValues[1].padStart(2, '0'))
    }
    return null
}

private fun validarAno(a: String): String? = a.takeIf { it.toInt() in 1950..2100 }

private fun montar(ano: String, mes: String, dia: String?): String? {
    validarAno(ano) ?: return null
    if (mes.toInt() !in 1..12) return null
    if (dia == null) return "$ano-$mes"
    return if (dia.toInt() in 1..31) "$ano-$mes-$dia" else null
}

/** "2011-04-18" -> "18/04/2011"; "2011-04" -> "04/2011"; "2011" -> "2011". */
fun lancamentoBr(iso: String?): String {
    val p = iso?.split("-") ?: return ""
    return when (p.size) {
        3 -> "${p[2]}/${p[1]}/${p[0]}"
        2 -> "${p[1]}/${p[0]}"
        else -> iso
    }
}

fun anoDe(lancamento: String?): String? = lancamento?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }

private val NOMES_PC = setOf("pc", "steam", "windows", "epic", "epicgames", "gog", "gogcom", "pcsteam", "steampc", "pcwindows", "windowspc")

/** Jogo de PC (ou sem plataforma): a Steam tem a capa certa. Console não. */
fun ehPlataformaPc(plataforma: String?): Boolean =
    plataforma.isNullOrBlank() || chaveNome(plataforma) in NOMES_PC

/** O que o "baixar em massa" deve fazer. */
data class MassaOpcoes(
    val capas: Boolean = true,
    val substituir: Boolean = false,
    val lancamento: Boolean = true,
    val consoles: Boolean = false,
)

fun precisaCapa(g: Game, o: MassaOpcoes): Boolean = o.capas && (g.capa == null || o.substituir)

fun precisaLancamento(g: Game, o: MassaOpcoes): Boolean = o.lancamento && g.lancamento == null

fun candidatosMassa(jogos: List<Game>, o: MassaOpcoes): List<Game> =
    jogos.filter { (o.consoles || ehPlataformaPc(it.plataforma)) && (precisaCapa(it, o) || precisaLancamento(it, o)) }

/** Chamadas à loja da Steam que o lote vai fazer (cada uma espera ~1,5 s para não tomar bloqueio). */
fun chamadasLoja(lista: List<Game>, o: MassaOpcoes): Int =
    lista.sumOf { (if (it.steamAppId == null) 1 else 0) + (if (precisaLancamento(it, o)) 1 else 0) }

/**
 * Entende o ID da Steam como o usuário cola: "1245620", ou o link da loja
 * ("https://store.steampowered.com/app/1245620/ELDEN_RING/"). Outra coisa -> null.
 */
fun extrairAppId(texto: String): Int? {
    val t = texto.trim()
    val doLink = Regex("/(?:app|a)/(\\d{1,9})").find(t)?.groupValues?.get(1)
    val numero = doLink ?: t.takeIf { it.isNotEmpty() && it.length <= 9 && it.all(Char::isDigit) }
    return numero?.toIntOrNull()?.takeIf { it > 0 }
}

// ---------- etiquetas ----------

/** "Platinado , 100%" -> ["Platinado", "100%"] (sem repetir, sem vazios, máx. 40 letras cada). */
fun lerTags(texto: String?): List<String> =
    (texto ?: "").split(',', ';', '\n').map { it.trim().take(40) }.filter { it.isNotEmpty() }
        .distinctBy { semAcento(it) }

fun juntarTags(tags: List<String>): String? =
    tags.map { it.replace(',', ' ').trim() }.filter { it.isNotEmpty() }.distinctBy { semAcento(it) }
        .joinToString(",").ifEmpty { null }

/** Todas as etiquetas usadas, da mais comum para a menos, cada uma uma vez (com a grafia mais usada). */
fun todasTags(jogos: List<Game>): List<String> =
    jogos.flatMap { it.tags }.groupBy { semAcento(it) }
        .values.sortedWith(compareByDescending<List<String>> { it.size }.thenBy { semAcento(it.first()) })
        .map { grupo -> grupo.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key }

// ---------- estatísticas e metas ----------

/** Zerados por ano da data em que zerou, do ano mais recente para o mais antigo. */
fun zeradosPorAno(jogos: List<Game>): List<Pair<Int, Int>> =
    jogos.filter { it.status == Status.ZERADO }
        .mapNotNull { it.dataZerou?.take(4)?.toIntOrNull() }
        .groupingBy { it }.eachCount()
        .toList().sortedByDescending { it.first }

/** Horas somadas por gênero (maiores primeiro). Jogos sem gênero ou sem horas ficam de fora. */
fun horasPorGenero(jogos: List<Game>, limite: Int = 8): List<Pair<String, Double>> =
    jogos.filter { !it.genero.isNullOrBlank() && (it.horas ?: 0.0) > 0.0 }
        .groupBy { semAcento(it.genero!!.trim()) }
        .map { (_, g) -> g.first().genero!!.trim() to g.sumOf { it.horas ?: 0.0 } }
        .sortedByDescending { it.second }.take(limite)

/**
 * Meses seguidos com pelo menos um jogo zerado: (atual, melhor).
 * A sequência atual continua viva se o mês passado teve zerada, mesmo que este ainda não tenha.
 */
fun sequenciaMeses(jogos: List<Game>, hoje: LocalDate): Pair<Int, Int> {
    val meses = jogos.filter { it.status == Status.ZERADO }.mapNotNull { j ->
        val p = j.dataZerou?.split("-")?.mapNotNull { it.toIntOrNull() }
        if (p != null && p.size >= 2 && p[1] in 1..12) YearMonth.of(p[0], p[1]) else null
    }.toSortedSet()
    if (meses.isEmpty()) return 0 to 0
    var melhor = 0
    var corrida = 0
    var anterior: YearMonth? = null
    for (m in meses) {
        corrida = if (anterior != null && anterior.plusMonths(1) == m) corrida + 1 else 1
        melhor = maxOf(melhor, corrida)
        anterior = m
    }
    val agora = YearMonth.from(hoje)
    var atual = 0
    var m = if (agora in meses) agora else agora.minusMonths(1)
    while (m in meses) {
        atual++
        m = m.minusMonths(1)
    }
    return atual to melhor
}

/** Quantos jogos foram zerados no [ano]. */
fun zeradosNoAno(jogos: List<Game>, ano: Int): Int =
    jogos.count { it.status == Status.ZERADO && it.dataZerou?.take(4)?.toIntOrNull() == ano }

/** Dias que um jogo fica na lixeira antes de ser apagado de vez. */
const val DIAS_LIXEIRA = 30
