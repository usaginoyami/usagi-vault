package com.usaginoyami.vault.data

enum class Status(val rotulo: String) {
    PENDENTE("Pendente"),
    ZERADO("Zerado ✔"),
    SEM_FIM("Sem fim ∞"),
    DROPADO("Dropei ✘"),
}

/** Um jogo do banco. As colunas seguem o mesmo jogos.db do app do PC. */
data class Game(
    val id: Long = 0,
    val nome: String,
    val plataforma: String? = null,
    val genero: String? = null,
    val status: Status = Status.PENDENTE,
    /** Data no formato yyyy-MM-dd (igual ao PC). Só vale quando zerado. */
    val dataZerou: String? = null,
    val horas: Double? = null,
    val nota: Double? = null,
    val videoUrl: String? = null,
    val obs: String? = null,
    /** Nome do arquivo da capa dentro da pasta de capas. */
    val capa: String? = null,
    /** Data de lançamento: "2011", "2011-04" ou "2011-04-18" (ISO, para ordenar por texto). */
    val lancamento: String? = null,
    /** ID do jogo na Steam, quando já descoberto (evita buscar de novo pelo nome). */
    val steamAppId: Int? = null,
)
