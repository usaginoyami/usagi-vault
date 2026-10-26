package com.usaginoyami.vault.data

import android.content.Context
import com.usaginoyami.vault.logic.melhorResultado
import com.usaginoyami.vault.logic.parseDataSteam
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

open class ErroSteam(mensagem: String) : Exception(mensagem)

/** A loja da Steam respondeu 429: pedindo para esperar. */
class LimiteSteam : ErroSteam("A Steam pediu para esperar um pouco (limite de pedidos). Tente de novo em alguns minutos.")

class SteamGame(val appid: Int, val nome: String, val horas: Double)

class SteamBusca(val appid: Int, val nome: String)

class DetalhesSteam(val nome: String, val lancamento: String?)

/** Chamadas bloqueantes à API da Steam (a chave fica só no celular). Use fora da thread principal. */
object Steam {
    private const val INTERVALO_LOJA_MS = 1500L
    private var ultimaChamadaLoja = 0L

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun baixar(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            val codigo = c.responseCode
            if (codigo == 429) throw LimiteSteam()
            if (codigo !in 200..299) throw IOException("HTTP $codigo")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private fun json(url: String): JSONObject = JSONObject(baixar(url).toString(Charsets.UTF_8))

    /** A loja (store.steampowered.com) bloqueia quem faz muitos pedidos: espaça as chamadas. */
    @Synchronized
    private fun esperarLoja() {
        val falta = ultimaChamadaLoja + INTERVALO_LOJA_MS - System.currentTimeMillis()
        if (falta > 0) Thread.sleep(falta)
        ultimaChamadaLoja = System.currentTimeMillis()
    }

    private fun traduzir(e: Exception): Exception = when {
        e is ErroSteam -> e
        e.message?.contains("403") == true -> ErroSteam("Chave da API inválida.")
        e is java.net.UnknownHostException || e is java.net.SocketTimeoutException ->
            ErroSteam("Sem conexão com a Steam. Confira a internet.")
        else -> ErroSteam("Erro ao falar com a Steam: ${e.message ?: e.javaClass.simpleName}")
    }

    /** Lista os jogos da conta. [quem] pode ser SteamID64, nome personalizado ou o link do perfil. */
    fun jogos(chave: String, quem: String): List<SteamGame> {
        try {
            var id = quem.trim().trimEnd('/').substringAfterLast('/')
            if (id.isEmpty() || !id.all { it.isDigit() }) {
                val perfil = json(
                    "https://api.steampowered.com/ISteamUser/ResolveVanityURL/v1/?key=${enc(chave)}&vanityurl=${enc(id)}"
                ).getJSONObject("response")
                if (perfil.optInt("success") != 1) {
                    throw ErroSteam("Não achei esse perfil. Use o SteamID64 ou o link do perfil.")
                }
                id = perfil.getString("steamid")
            }
            val resposta = json(
                "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/?key=${enc(chave)}&steamid=${enc(id)}" +
                    "&include_appinfo=1&include_played_free_games=1&format=json"
            ).getJSONObject("response")
            val lista = resposta.optJSONArray("games")
                ?: throw ErroSteam("Nenhum jogo retornado. Deixe o perfil e os 'detalhes dos jogos' públicos.")
            val saida = ArrayList<SteamGame>(lista.length())
            for (i in 0 until lista.length()) {
                val g = lista.getJSONObject(i)
                val nome = g.optString("name").trim()
                if (nome.isEmpty()) continue
                val horas = Math.round(g.optInt("playtime_forever") / 6.0) / 10.0
                saida += SteamGame(g.getInt("appid"), nome, horas)
            }
            return saida
        } catch (e: Exception) {
            throw traduzir(e)
        }
    }

    /** Baixa a capa do jogo (retrato; se não houver, o banner) e devolve o nome do arquivo salvo. */
    fun capa(ctx: Context, appid: Int): String? {
        val base = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appid/"
        for (arquivo in listOf("library_600x900.jpg", "header.jpg")) {
            val bytes = try {
                baixar(base + arquivo)
            } catch (e: LimiteSteam) {
                throw e
            } catch (e: Exception) {
                continue
            }
            Covers.salvarBytes(ctx, bytes)?.let { return it }
        }
        return null
    }

    /**
     * Procura um jogo pelo nome na loja da Steam.
     * [estrito] = só aceita nome igual (ou igual + subtítulo); usado no lote, onde um palpite errado poria capa trocada.
     */
    fun buscarPorNome(nome: String, estrito: Boolean = false): SteamBusca? {
        try {
            esperarLoja()
            val itens = json("https://store.steampowered.com/api/storesearch/?term=${enc(nome)}&l=portuguese&cc=BR")
                .optJSONArray("items") ?: return null
            val achados = ArrayList<SteamBusca>()
            for (i in 0 until itens.length()) {
                val item = itens.getJSONObject(i)
                val n = item.optString("name")
                if (n.isNotEmpty() && item.has("id")) achados += SteamBusca(item.getInt("id"), n)
            }
            val k = melhorResultado(nome, achados.map { it.nome }, estrito)
            return if (k >= 0) achados[k] else null
        } catch (e: Exception) {
            throw traduzir(e)
        }
    }

    /** Nome e data de lançamento de um ID da Steam, ou null se o ID não existe. */
    fun detalhes(appid: Int): DetalhesSteam? {
        try {
            esperarLoja()
            val raiz = json("https://store.steampowered.com/api/appdetails?appids=$appid&filters=basic,release_date&cc=br&l=english")
            val item = raiz.optJSONObject(appid.toString()) ?: return null
            if (!item.optBoolean("success")) return null
            val data = item.optJSONObject("data") ?: return null
            val nome = data.optString("name").ifBlank { "ID $appid" }
            val texto = data.optJSONObject("release_date")?.optString("date").orEmpty()
            return DetalhesSteam(nome, parseDataSteam(texto))
        } catch (e: Exception) {
            throw traduzir(e)
        }
    }

    /** Data de lançamento do jogo ("2011-04-18", "2011-04" ou "2011"), ou null se a Steam não informa. */
    fun lancamento(appid: Int): String? {
        try {
            esperarLoja()
            val raiz = json("https://store.steampowered.com/api/appdetails?appids=$appid&filters=release_date&cc=br&l=english")
            val item = raiz.optJSONObject(appid.toString()) ?: return null
            if (!item.optBoolean("success")) return null
            val data = item.optJSONObject("data") ?: return null
            val texto = data.optJSONObject("release_date")?.optString("date").orEmpty()
            return parseDataSteam(texto)
        } catch (e: Exception) {
            throw traduzir(e)
        }
    }
}
