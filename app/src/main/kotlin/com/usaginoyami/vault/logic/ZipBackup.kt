package com.usaginoyami.vault.logic

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup = um .zip com jogos.db e a pasta capas/ (o mesmo formato do app do PC).
 * Só java.io, para poder ser testado fora do Android.
 */
object ZipBackup {
    enum class Tipo { BANCO, CAPA, IGNORAR }

    class Extraido(val banco: File?, val capas: Int)

    private val NOME_CAPA = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}\\.(jpg|jpeg|png|webp|gif)$", RegexOption.IGNORE_CASE)

    fun nomeCapaValido(nome: String): Boolean = NOME_CAPA.matches(nome)

    /** Decide o que fazer com uma entrada do zip, olhando só o último nome e a pasta de cima. */
    fun tipo(entrada: String): Tipo {
        val partes = entrada.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        val nome = partes.lastOrNull() ?: return Tipo.IGNORAR
        val pai = partes.getOrNull(partes.size - 2)
        return when {
            nome.endsWith(".db", ignoreCase = true) && nome.length > 3 -> Tipo.BANCO
            pai.equals("capas", ignoreCase = true) && nomeCapaValido(nome) -> Tipo.CAPA
            else -> Tipo.IGNORAR
        }
    }

    fun ultimoNome(entrada: String): String =
        entrada.replace('\\', '/').split('/').filter { it.isNotEmpty() }.last()

    /** Escreve o jogos.db e a pasta capas no zip. Devolve quantas capas foram. */
    fun gravar(banco: File, capasDir: File, destino: OutputStream): Int {
        var n = 0
        ZipOutputStream(destino.buffered()).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            zip.putNextEntry(ZipEntry("jogos.db"))
            banco.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            capasDir.listFiles()?.filter { it.isFile && nomeCapaValido(it.name) }?.sortedBy { it.name }?.forEach { f ->
                zip.putNextEntry(ZipEntry("capas/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                n++
            }
        }
        return n
    }

    /**
     * Abre o zip e coloca o banco em [pasta]/jogos.db e as capas em [pasta]/capas/.
     * Nomes vêm sempre do último pedaço do caminho e passam por validação (sem "../").
     */
    fun extrair(zip: InputStream, pasta: File): Extraido {
        val capasDir = File(pasta, "capas").also { it.mkdirs() }
        val bancos = LinkedHashMap<String, File>()
        var capas = 0
        ZipInputStream(zip.buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    when (tipo(e.name)) {
                        Tipo.BANCO -> {
                            val nome = ultimoNome(e.name)
                            val alvo = File(pasta, "db_${bancos.size}.tmp")
                            alvo.outputStream().use { z.copyTo(it) }
                            bancos[nome.lowercase()] = alvo
                        }
                        Tipo.CAPA -> {
                            File(capasDir, ultimoNome(e.name)).outputStream().use { z.copyTo(it) }
                            capas++
                        }
                        Tipo.IGNORAR -> Unit
                    }
                }
                z.closeEntry()
                e = z.nextEntry
            }
        }
        val escolhido = bancos["jogos.db"] ?: bancos.values.firstOrNull()
        val resultado = escolhido?.let { File(pasta, "jogos.db").also { dest -> it.copyTo(dest, overwrite = true) } }
        bancos.values.forEach { it.delete() }
        return Extraido(resultado, capas)
    }

    private val ASSINATURA_SQLITE = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)

    fun ehSqlite(arquivo: File): Boolean = lerInicio(arquivo, ASSINATURA_SQLITE.size).contentEquals(ASSINATURA_SQLITE)

    fun ehZip(arquivo: File): Boolean {
        val b = lerInicio(arquivo, 2)
        return b.size == 2 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()
    }

    private fun lerInicio(arquivo: File, n: Int): ByteArray =
        arquivo.inputStream().use { i ->
            val buf = ByteArray(n)
            var lidos = 0
            while (lidos < n) {
                val r = i.read(buf, lidos, n - lidos)
                if (r < 0) break
                lidos += r
            }
            buf.copyOf(lidos)
        }
}
