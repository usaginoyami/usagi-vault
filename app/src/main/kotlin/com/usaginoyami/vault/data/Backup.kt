package com.usaginoyami.vault.data

import android.content.Context
import android.net.Uri
import com.usaginoyami.vault.logic.ZipBackup
import java.io.File
import java.io.IOException

class ErroBackup(mensagem: String) : Exception(mensagem)

/** Exporta/importa a biblioteca inteira (jogos.db + capas) como um .zip, igual ao app do PC. */
object Backup {
    /** Devolve quantas capas foram para o zip. */
    fun exportar(ctx: Context, banco: GameDb, destino: Uri): Int =
        banco.comBancoFechado {
            val saida = ctx.contentResolver.openOutputStream(destino)
                ?: throw IOException("Não consegui criar o arquivo.")
            ZipBackup.gravar(banco.arquivo, Covers.dir(ctx), saida)
        }

    /** Troca a biblioteca atual pelo conteúdo do arquivo (.zip ou .db). Devolve quantas capas vieram. */
    fun importar(ctx: Context, banco: GameDb, origem: Uri): Int {
        val trabalho = File(ctx.cacheDir, "importar").also { it.deleteRecursively(); it.mkdirs() }
        try {
            val bruto = File(trabalho, "entrada.tmp")
            val entrada = ctx.contentResolver.openInputStream(origem) ?: throw ErroBackup("Não consegui abrir o arquivo.")
            entrada.use { i -> bruto.outputStream().use { o -> i.copyTo(o) } }

            val novoBanco: File
            var capas = 0
            when {
                ZipBackup.ehSqlite(bruto) -> novoBanco = bruto
                ZipBackup.ehZip(bruto) -> {
                    val extraido = bruto.inputStream().use { ZipBackup.extrair(it, File(trabalho, "zip")) }
                    novoBanco = extraido.banco ?: throw ErroBackup("Não achei o jogos.db dentro do zip.")
                    capas = extraido.capas
                }
                else -> throw ErroBackup("Escolha um .zip de backup ou um arquivo jogos.db.")
            }

            GameDb.validar(novoBanco)?.let { throw ErroBackup(it) }

            val capasNovas = File(trabalho, "zip/capas")
            banco.comBancoFechado {
                // guarda uma cópia da biblioteca anterior, por segurança
                if (banco.arquivo.exists()) {
                    banco.arquivo.copyTo(File(banco.arquivo.parentFile, "jogos-anterior.db"), overwrite = true)
                }
                val temporario = File(banco.arquivo.path + ".novo")
                novoBanco.copyTo(temporario, overwrite = true)
                banco.arquivo.delete()
                if (!temporario.renameTo(banco.arquivo)) {
                    temporario.copyTo(banco.arquivo, overwrite = true)
                    temporario.delete()
                }
                capasNovas.listFiles()?.forEach { it.copyTo(File(Covers.dir(ctx), it.name), overwrite = true) }
            }
            return capas
        } finally {
            trabalho.deleteRecursively()
        }
    }
}
