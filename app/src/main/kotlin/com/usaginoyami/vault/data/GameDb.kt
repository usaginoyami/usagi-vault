package com.usaginoyami.vault.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Jogo novo vindo da Steam. */
class NovoJogo(val nome: String, val horas: Double?, val capa: String?, val steamAppId: Int? = null)

/**
 * Banco SQLite com o mesmo esquema do jogos.db do PC (tabela "jogos").
 * Todas as chamadas são bloqueantes: use fora da thread principal.
 */
class GameDb(val arquivo: File) {
    private var aberto: SQLiteDatabase? = null

    private val db: SQLiteDatabase
        @Synchronized get() = aberto ?: abrir().also { aberto = it }

    private fun abrir(): SQLiteDatabase {
        arquivo.parentFile?.mkdirs()
        val d = SQLiteDatabase.openOrCreateDatabase(arquivo.path, null)
        garantirEsquema(d)
        return d
    }

    private fun garantirEsquema(d: SQLiteDatabase) {
        d.execSQL(
            """CREATE TABLE IF NOT EXISTS jogos (
                id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT NOT NULL, plataforma TEXT, genero TEXT,
                zerado INTEGER DEFAULT 0, data_zerou TEXT, horas REAL, nota REAL, video_url TEXT, obs TEXT)"""
        )
        val colunas = mutableSetOf<String>()
        d.rawQuery("PRAGMA table_info(jogos)", null).use { c ->
            while (c.moveToNext()) colunas += c.getString(1)
        }
        if ("capa" !in colunas) d.execSQL("ALTER TABLE jogos ADD COLUMN capa TEXT")
        if ("sem_fim" !in colunas) d.execSQL("ALTER TABLE jogos ADD COLUMN sem_fim INTEGER DEFAULT 0")
        if ("dropado" !in colunas) d.execSQL("ALTER TABLE jogos ADD COLUMN dropado INTEGER DEFAULT 0")
        if ("lancamento" !in colunas) d.execSQL("ALTER TABLE jogos ADD COLUMN lancamento TEXT")
        if ("steam_appid" !in colunas) d.execSQL("ALTER TABLE jogos ADD COLUMN steam_appid INTEGER")
    }

    @Synchronized
    fun todos(): List<Game> =
        db.rawQuery("SELECT * FROM jogos", null).use { c ->
            buildList { while (c.moveToNext()) add(c.paraJogo()) }
        }

    /** Insere (id = 0) ou atualiza. Devolve o id. */
    @Synchronized
    fun salvar(g: Game): Long {
        val v = valores(g)
        return if (g.id > 0) {
            db.update("jogos", v, "id=?", arrayOf(g.id.toString()))
            g.id
        } else {
            db.insertOrThrow("jogos", null, v)
        }
    }

    /** Apaga o jogo e devolve o nome do arquivo da capa (para o chamador apagar o arquivo). */
    @Synchronized
    fun excluir(id: Long): String? {
        val capa = db.rawQuery("SELECT capa FROM jogos WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        db.delete("jogos", "id=?", arrayOf(id.toString()))
        return capa
    }

    @Synchronized
    fun inserirSteam(novos: List<NovoJogo>) {
        db.beginTransaction()
        try {
            for (n in novos) {
                val v = ContentValues().apply {
                    put("nome", n.nome)
                    put("plataforma", "Steam")
                    put("zerado", 0)
                    put("sem_fim", 0)
                    put("dropado", 0)
                    if (n.horas != null && n.horas > 0.0) put("horas", n.horas) else putNull("horas")
                    if (n.capa != null) put("capa", n.capa) else putNull("capa")
                    if (n.steamAppId != null) put("steam_appid", n.steamAppId) else putNull("steam_appid")
                }
                db.insertOrThrow("jogos", null, v)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Grava o que veio da Steam (id, capa nova, data de lançamento); só mexe no que não for null.
     * Devolve o nome da capa antiga quando ela foi trocada, para o chamador apagar o arquivo.
     */
    @Synchronized
    fun atualizarSteam(id: Long, appid: Int, capa: String?, lancamento: String?): String? {
        val antiga = if (capa == null) null else
            db.rawQuery("SELECT capa FROM jogos WHERE id=?", arrayOf(id.toString())).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        val v = ContentValues().apply {
            put("steam_appid", appid)
            if (capa != null) put("capa", capa)
            if (lancamento != null) put("lancamento", lancamento)
        }
        db.update("jogos", v, "id=?", arrayOf(id.toString()))
        return antiga
    }

    @Synchronized
    fun capasEmUso(): Set<String> =
        db.rawQuery("SELECT capa FROM jogos WHERE capa IS NOT NULL", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    /**
     * Fecha o banco, roda [bloco] com o arquivo livre (para copiar ou trocar) e reabre depois.
     * Ao fechar, o SQLite grava tudo no arquivo principal.
     */
    @Synchronized
    fun <T> comBancoFechado(bloco: () -> T): T {
        aberto?.close()
        aberto = null
        try {
            return bloco()
        } finally {
            File(arquivo.path + "-wal").delete()
            File(arquivo.path + "-shm").delete()
            File(arquivo.path + "-journal").delete()
            aberto = abrir()
        }
    }

    private fun valores(g: Game): ContentValues = ContentValues().apply {
        put("nome", g.nome.trim())
        putTexto("plataforma", g.plataforma)
        putTexto("genero", g.genero)
        put("zerado", if (g.status == Status.ZERADO) 1 else 0)
        put("sem_fim", if (g.status == Status.SEM_FIM) 1 else 0)
        put("dropado", if (g.status == Status.DROPADO) 1 else 0)
        putTexto("data_zerou", if (g.status == Status.ZERADO) g.dataZerou else null)
        if (g.horas != null) put("horas", g.horas) else putNull("horas")
        if (g.nota != null) put("nota", g.nota) else putNull("nota")
        putTexto("video_url", g.videoUrl)
        putTexto("obs", g.obs)
        putTexto("capa", g.capa)
        putTexto("lancamento", g.lancamento)
        if (g.steamAppId != null) put("steam_appid", g.steamAppId) else putNull("steam_appid")
    }

    private fun ContentValues.putTexto(chave: String, valor: String?) {
        val t = valor?.trim()
        if (t.isNullOrEmpty()) putNull(chave) else put(chave, t)
    }

    private fun Cursor.paraJogo(): Game {
        fun idx(n: String) = getColumnIndexOrThrow(n)
        fun texto(n: String): String? = getString(idx(n))?.takeIf { it.isNotBlank() }
        fun inteiro(n: String): Int = idx(n).let { if (isNull(it)) 0 else getInt(it) }
        fun decimal(n: String): Double? = idx(n).let { if (isNull(it)) null else getDouble(it) }
        fun inteiroNulo(n: String): Int? = idx(n).let { if (isNull(it)) null else getInt(it) }
        val status = when {
            inteiro("zerado") != 0 -> Status.ZERADO
            inteiro("sem_fim") != 0 -> Status.SEM_FIM
            inteiro("dropado") != 0 -> Status.DROPADO
            else -> Status.PENDENTE
        }
        return Game(
            id = getLong(idx("id")),
            nome = getString(idx("nome")).orEmpty(),
            plataforma = texto("plataforma"),
            genero = texto("genero"),
            status = status,
            dataZerou = texto("data_zerou"),
            horas = decimal("horas"),
            nota = decimal("nota"),
            videoUrl = texto("video_url"),
            obs = texto("obs"),
            capa = texto("capa"),
            lancamento = texto("lancamento"),
            steamAppId = inteiroNulo("steam_appid"),
        )
    }

    companion object {
        /** null se o arquivo é um banco com a tabela de jogos; senão, a mensagem de erro. */
        fun validar(arquivo: File): String? = try {
            val d = SQLiteDatabase.openDatabase(arquivo.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val colunas = mutableSetOf<String>()
                d.rawQuery("PRAGMA table_info(jogos)", null).use { c ->
                    while (c.moveToNext()) colunas += c.getString(1)
                }
                if ("nome" !in colunas) "Esse arquivo não tem a tabela de jogos." else null
            } finally {
                d.close()
            }
        } catch (e: Exception) {
            "Esse arquivo não é um banco de jogos válido."
        }
    }
}
