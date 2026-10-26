package com.usaginoyami.vault.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID

/** Capas ficam em arquivos .jpg dentro de filesDir/capas; o banco guarda só o nome do arquivo. */
object Covers {
    private const val LARGURA = 420

    fun dir(ctx: Context): File = File(ctx.filesDir, "capas").also { it.mkdirs() }

    fun apagar(ctx: Context, nome: String?) {
        if (nome.isNullOrBlank()) return
        File(dir(ctx), File(nome).name).delete()
    }

    /** Apaga capas que nenhum jogo usa mais. */
    fun limparOrfas(ctx: Context, emUso: Set<String>) {
        dir(ctx).listFiles()?.forEach { if (it.isFile && it.name !in emUso) it.delete() }
    }

    fun salvarUri(ctx: Context, uri: Uri): String {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Não consegui abrir a imagem.")
        return salvarBytes(ctx, bytes) ?: throw IOException("Esse arquivo não é uma imagem.")
    }

    /** Reduz para 420 px de largura, grava como JPEG e devolve o nome do arquivo (ou null se não for imagem). */
    fun salvarBytes(ctx: Context, bytes: ByteArray): String? {
        val bmp = reduzir(bytes) ?: return null
        val nome = UUID.randomUUID().toString().replace("-", "") + ".jpg"
        File(dir(ctx), nome).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return nome
    }

    private fun reduzir(bytes: ByteArray): Bitmap? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
        var amostra = 1
        while (limites.outWidth / (amostra * 2) >= LARGURA) amostra *= 2
        val opcoes = BitmapFactory.Options().apply { inSampleSize = amostra }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes) ?: return null
        if (bmp.width <= LARGURA) return bmp
        val altura = (bmp.height * LARGURA.toFloat() / bmp.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, LARGURA, altura, true)
    }
}
