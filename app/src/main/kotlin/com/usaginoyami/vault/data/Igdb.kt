package com.usaginoyami.vault.data

import android.content.Context
import com.usaginoyami.vault.logic.CandIgdb
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Client ID/Secret recusados pela Twitch (o IGDB usa a conta da Twitch). */
class ErroChaveIgdb : Exception("Client ID ou Secret do IGDB inválido. Confira em dev.twitch.tv/console.")

class LimiteIgdb : LimiteApi("O IGDB pediu para esperar um pouco (limite de pedidos).")

/**
 * IGDB (capas de caixa, datas por plataforma, gêneros) para jogos de console.
 * Precisa de Client ID e Secret grátis da Twitch; ficam só no celular. Chamadas bloqueantes: use fora da thread principal.
 */
object Igdb {
    private const val INTERVALO_MS = 300L // o IGDB aceita 4 pedidos por segundo
    private var ultimaChamada = 0L
    private var token: String? = null
    private var tokenDe: String = ""
    private var expiraEm = 0L

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Synchronized
    private fun esperar() {
        val falta = ultimaChamada + INTERVALO_MS - System.currentTimeMillis()
        if (falta > 0) Thread.sleep(falta)
        ultimaChamada = System.currentTimeMillis()
    }

    @Synchronized
    private fun obterToken(id: String, secret: String): String {
        val chave = "$id:$secret"
        val atual = token
        if (atual != null && tokenDe == chave && System.currentTimeMillis() < expiraEm) return atual
        val c = URL("https://id.twitch.tv/oauth2/token").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20_000
            c.readTimeout = 20_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use {
                it.write("client_id=${enc(id)}&client_secret=${enc(secret)}&grant_type=client_credentials".toByteArray())
            }
            val codigo = c.responseCode
            if (codigo == 400 || codigo == 401 || codigo == 403) throw ErroChaveIgdb()
            if (codigo !in 200..299) throw IOException("HTTP $codigo")
            val json = JSONObject(c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8))
            val novo = json.getString("access_token")
            token = novo
            tokenDe = chave
            expiraEm = System.currentTimeMillis() + (json.optLong("expires_in", 3600) - 300) * 1000
            return novo
        } finally {
            c.disconnect()
        }
    }

    private fun consulta(id: String, secret: String, corpo: String, tentarDeNovo: Boolean = true): JSONArray {
        esperar()
        val t = obterToken(id, secret)
        val c = URL("https://api.igdb.com/v4/games").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20_000
            c.readTimeout = 20_000
            c.doOutput = true
            c.setRequestProperty("Client-ID", id)
            c.setRequestProperty("Authorization", "Bearer $t")
            c.setRequestProperty("Accept", "application/json")
            c.outputStream.use { it.write(corpo.toByteArray()) }
            val codigo = c.responseCode
            if (codigo == 429) throw LimiteIgdb()
            if (codigo == 401 && tentarDeNovo) {
                synchronized(this) { token = null } // token vencido: pega outro e tenta uma vez
                return consulta(id, secret, corpo, false)
            }
            if (codigo == 401 || codigo == 403) throw ErroChaveIgdb()
            if (codigo !in 200..299) throw IOException("HTTP $codigo")
            return JSONArray(c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8))
        } finally {
            c.disconnect()
        }
    }

    private fun traduzir(e: Exception): Exception = when {
        e is ErroChaveIgdb || e is LimiteApi -> e
        e is java.net.UnknownHostException || e is java.net.SocketTimeoutException ->
            ErroSteam("Sem conexão com o IGDB. Confira a internet.")
        else -> ErroSteam("Erro ao falar com o IGDB: ${e.message ?: e.javaClass.simpleName}")
    }

    /** Procura jogos pelo nome; quem escolhe o certo (por plataforma) é `escolherIgdb`. */
    fun buscar(id: String, secret: String, nome: String): List<CandIgdb> {
        try {
            val seguro = nome.replace("\\", " ").replace("\"", " ").trim()
            val corpo = "search \"$seguro\"; " +
                "fields name,cover.image_id,first_release_date,genres.name,platforms.name," +
                "release_dates.date,release_dates.platform.name; limit 30;"
            val lista = consulta(id.trim(), secret.trim(), corpo)
            val saida = ArrayList<CandIgdb>(lista.length())
            for (i in 0 until lista.length()) {
                val j = lista.getJSONObject(i)
                val nomeJogo = j.optString("name")
                if (nomeJogo.isBlank()) continue
                val plataformas = HashSet<String>()
                j.optJSONArray("platforms")?.let { a ->
                    for (k in 0 until a.length()) a.optJSONObject(k)?.optString("name")?.takeIf { it.isNotBlank() }?.let(plataformas::add)
                }
                val datas = HashMap<String, Long>()
                j.optJSONArray("release_dates")?.let { a ->
                    for (k in 0 until a.length()) {
                        val r = a.optJSONObject(k) ?: continue
                        val p = r.optJSONObject("platform")?.optString("name").orEmpty()
                        if (p.isBlank() || !r.has("date")) continue
                        val d = r.optLong("date")
                        val antes = datas[p]
                        if (antes == null || d < antes) datas[p] = d
                    }
                }
                val generos = ArrayList<String>()
                j.optJSONArray("genres")?.let { a ->
                    for (k in 0 until a.length()) a.optJSONObject(k)?.optString("name")?.takeIf { it.isNotBlank() }?.let(generos::add)
                }
                saida += CandIgdb(
                    id = j.optLong("id"),
                    nome = nomeJogo,
                    imageId = j.optJSONObject("cover")?.optString("image_id")?.takeIf { it.isNotBlank() },
                    plataformas = plataformas,
                    datas = datas,
                    primeira = if (j.has("first_release_date")) j.optLong("first_release_date") else null,
                    generos = generos,
                )
            }
            return saida
        } catch (e: Exception) {
            throw traduzir(e)
        }
    }

    /** Baixa a capa de caixa e devolve o nome do arquivo salvo (ou null se não deu). */
    fun capa(ctx: Context, imageId: String): String? {
        esperar()
        val c = URL("https://images.igdb.com/igdb/image/upload/t_cover_big_2x/$imageId.jpg").openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 20_000
            c.readTimeout = 20_000
            if (c.responseCode !in 200..299) return null
            Covers.salvarBytes(ctx, c.inputStream.use { it.readBytes() })
        } catch (e: Exception) {
            null
        } finally {
            c.disconnect()
        }
    }
}
