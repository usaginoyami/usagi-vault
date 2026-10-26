#!/usr/bin/env python3
"""Leva o banco do Meus Jogos (PC) para o Usagi Vault (celular) e traz de volta.

Rode DENTRO da pasta do Meus Jogos (onde ficam jogos.db e a pasta capas):

    python sync_celular.py levar                      -> cria usagi-vault-backup.zip
    python sync_celular.py trazer usagi-vault-2026-10-19.zip
                                                      -> troca jogos.db e capas pelo zip do celular
                                                         (guarda o jogos.db antigo como jogos-antes.db)
Só usa a biblioteca padrão do Python.
"""
import os
import re
import shutil
import sqlite3
import sys
import tempfile
import zipfile

DB, CAPAS = "jogos.db", "capas"
NOME_CAPA = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,99}\.(jpg|jpeg|png|webp|gif)$", re.I)


def levar(saida="usagi-vault-backup.zip"):
    if not os.path.isfile(DB):
        sys.exit(f"Não achei {DB} nesta pasta. Rode o script dentro da pasta do Meus Jogos.")
    with tempfile.TemporaryDirectory() as tmp:
        copia = os.path.join(tmp, DB)  # cópia consistente, mesmo com o app aberto
        origem, destino = sqlite3.connect(DB), sqlite3.connect(copia)
        origem.backup(destino)
        origem.close()
        destino.close()
        n = 0
        with zipfile.ZipFile(saida, "w", zipfile.ZIP_DEFLATED) as z:
            z.write(copia, "jogos.db")
            if os.path.isdir(CAPAS):
                for nome in sorted(os.listdir(CAPAS)):
                    if NOME_CAPA.match(nome) and os.path.isfile(os.path.join(CAPAS, nome)):
                        z.write(os.path.join(CAPAS, nome), f"capas/{nome}")
                        n += 1
    print(f"Pronto: {saida} ({n} capas). Passe para o celular e use ⋮ > Importar backup no Usagi Vault.")


def trazer(arquivo):
    if not zipfile.is_zipfile(arquivo):
        sys.exit("Esse arquivo não é um .zip de backup.")
    with zipfile.ZipFile(arquivo) as z:
        bancos = {}
        capas = []
        for info in z.infolist():
            if info.is_dir():
                continue
            partes = [p for p in info.filename.replace("\\", "/").split("/") if p]
            nome, pai = partes[-1], (partes[-2] if len(partes) > 1 else "")
            if nome.lower().endswith(".db") and len(nome) > 3:
                bancos[nome.lower()] = info
            elif pai.lower() == "capas" and NOME_CAPA.match(nome):
                capas.append((info, nome))
        escolhido = bancos.get("jogos.db") or next(iter(bancos.values()), None)
        if escolhido is None:
            sys.exit("Não achei o jogos.db dentro do zip.")
        if os.path.isfile(DB):
            shutil.copy2(DB, "jogos-antes.db")
        os.makedirs(CAPAS, exist_ok=True)
        with z.open(escolhido) as i, open(DB, "wb") as o:
            shutil.copyfileobj(i, o)
        for info, nome in capas:
            with z.open(info) as i, open(os.path.join(CAPAS, nome), "wb") as o:
                shutil.copyfileobj(i, o)
    print(f"Pronto: {DB} atualizado e {len(capas)} capas copiadas. O antigo ficou em jogos-antes.db.")


if __name__ == "__main__":
    if len(sys.argv) >= 2 and sys.argv[1] == "levar":
        levar(*sys.argv[2:3])
    elif len(sys.argv) == 3 and sys.argv[1] == "trazer":
        trazer(sys.argv[2])
    else:
        print(__doc__)
