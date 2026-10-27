# Usagi Vault

Seu banco de jogos (o **Meus Jogos** do PC) em app Android. Kotlin + Jetpack Compose, tema preto e vermelho do UsagiNoYami.

- Grade de capas com selo **Zerado / Sem fim ∞ / Dropado**, busca (sem ligar para acento), filtros e ordenação (nome, nota, horas, data que zerou, **lançamento** do mais antigo ou do mais novo)
- Botão **▲/▼** no topo esconde/mostra estatísticas, busca e filtros (sobra mais espaço para as capas; o app lembra a escolha)
- Estatísticas no topo: jogos, zerados, sem fim, dropados, pendentes, horas jogadas, nota média
- Toque num jogo para editar; **+** para criar. Capa pela galeria ou **Buscar na Steam**
- **Importar da Steam** (⬇ Steam): lista seus jogos, você marca os que quer e ele baixa as capas
- **Capas em massa** (⋮ ▸ Baixar capas em massa): procura cada jogo na Steam e preenche a capa e a data de lançamento (veja abaixo)
- **Backup** (⋮): exporta/importa um `.zip` com `jogos.db` + `capas/` — o mesmo formato do app do PC
- Os dados ficam só no celular. A permissão de internet existe **apenas** para a Steam

> Este projeto foi escrito sem o Android SDK à mão, então não foi compilado nem rodado num aparelho aqui.
> A lógica pura (filtros, estatísticas, backup .zip, proteção contra zip malicioso) e o SQL foram testados à parte.
> Se o Android Studio reclamar de algo, mande o erro que eu corrijo.

## Como compilar

1. Android Studio → **Open** → pasta `usagi-vault` → confie no projeto.
2. Espere o Gradle sincronizar (JDK 17 ou 21; projeto usa Gradle 8.11.1 / AGP 8.7.3 / Kotlin 2.0.21).
3. **Build ▸ Build APK(s)** (debug) ou rode direto no celular.

## Levar os jogos do PC para o celular

No PC, dentro da pasta do Meus Jogos (onde ficam `jogos.db` e `capas`):

```
python sync_celular.py levar
```

(copie `sync_celular.py` e `levar_para_celular.bat`, da pasta `pc/`, para lá; o `.bat` é só um duplo clique para o comando `levar`). Isso cria `usagi-vault-backup.zip`.
Mande o zip para o celular (cabo, Drive, Telegram…) e, no app, **⋮ ▸ Importar backup**.

Sem o script também funciona: selecione `jogos.db` e a pasta `capas`, botão direito ▸ *Compactar em arquivo ZIP*.
Dá para importar só o `jogos.db` também, mas aí as capas não vêm.

## Trazer do celular para o PC

No app: **⋮ ▸ Exportar backup (.zip)**. No PC, na pasta do Meus Jogos:

```
python sync_celular.py trazer usagi-vault-2026-10-19.zip
```

Isso troca `jogos.db` e as capas (o `jogos.db` antigo fica como `jogos-antes.db`).

## Importar da Steam

1. Pegue sua chave em <https://steamcommunity.com/dev/apikey>.
2. Deixe o perfil e os "detalhes dos jogos" **públicos** na hora de importar.
3. No app: **⬇ Steam** ▸ chave + SteamID64 ou link do perfil ▸ **Buscar meus jogos** ▸ marque ▸ **Importar**.

Só aparecem jogos que ainda não estão no banco (compara o nome sem acento/símbolos, então "Hades™" e "Hades" contam como o mesmo).
A chave fica salva só no celular e **não** vai para o backup.

## Ordenar por lançamento e capas em massa

O campo **Lançamento** de cada jogo aceita `2011`, `04/2011` ou `18/04/2011`. Dá para digitar na edição (útil para jogos de console)
ou deixar o app buscar na Steam. Na lista, **Ordem ▾** tem *Lançamento (antigos 1º)* e *(novos 1º)*; quem não tem data fica no fim.

**⋮ ▸ Baixar capas em massa** (precisa de internet):

- Baixa a capa de quem está sem capa; opcionalmente **substitui** as que já existem pela capa retrato da Steam.
- Busca a data de lançamento de quem ainda não tem.
- Por padrão só mexe em jogos de **PC/Steam/sem plataforma**. Jogos de console ficam de fora (a capa da Steam é a do PC), a não ser que você marque a opção.
- A busca é **estrita**: só aceita nome igual (ou igual + subtítulo, como "The Witcher 3: Wild Hunt"). Prefere deixar um jogo sem capa a pôr a capa errada; esses aparecem numa lista no final para você resolver na edição (**Buscar na Steam** ou galeria).
- A loja da Steam limita pedidos, então o app espera ~1,5 s entre as consultas: uns 350 jogos levam **vários minutos**. Deixe o app aberto (a tela fica ligada); dá para **Parar** a qualquer hora e o que já foi baixado fica salvo.
- Se a Steam pedir para esperar (erro 429), o app pausa 1 minuto e tenta de novo; se repetir, para e avisa.

Os jogos importados da Steam pelo app já guardam o ID da Steam, então o lote não precisa procurar pelo nome.

> O banco ganhou duas colunas novas (`lancamento` e `steam_appid`). O app do PC (`jogos_app.py`) continua funcionando e simplesmente ignora as colunas;
> a ordenação por lançamento é só do Usagi Vault por enquanto.

## Salvamento automático, arrastar e ID da Steam

- **Salva sozinho**: na edição não há botão Salvar. Tudo que você digita ou muda é gravado logo em seguida (texto após ~0,7 s parado; status/capa quase na hora) e também ao voltar, trocar de jogo ou sair do app. O topo mostra "✓ Salvo". Jogo novo só é criado depois que tiver nome.
- **Arraste para o lado** na edição: esquerda = próximo jogo, direita = anterior (na ordem da lista que estava na tela, com filtro/busca/ordem). Também há os botões ‹ ›.
- **ID da Steam**: campo na edição (aceita o número ou o link da loja). Se a capa/data vieram de um jogo parecido (remake, remaster), cole o ID certo e toque em **Baixar capa e data por este ID**. O ID fica salvo e o download em massa passa a usá-lo. **Ver na Steam** abre a página; sem ID, **Procurar na Steam** abre a busca pelo nome.

## Etiquetas, lixeira e estatísticas (v1.4)

- **Etiquetas**: além do status (zerado, sem fim...), cada jogo pode ter várias etiquetas livres (ex.: `platinado`, `100%`, `coop`). Na edição: digite e toque em Adicionar, ou use as sugestões; toque numa etiqueta para tirar. Na biblioteca há uma linha de filtro `#etiqueta`, a busca também acha etiquetas e elas aparecem no cartão.
- **Lixeira**: excluir manda o jogo (e a capa) para a lixeira e o aviso tem **Desfazer**. Em ⋮ ▸ **Lixeira** dá para restaurar, apagar de vez ou esvaziar. O que passa de 30 dias lá é apagado sozinho.
- **Estatísticas e meta** (⋮ ▸ *Estatísticas e meta*): meta de zerados do ano com barra de progresso e ritmo necessário por mês, sequência de meses seguidos zerando algo, zerados por ano e horas por gênero. Só conta jogos zerados que têm data.

> O banco ganhou as colunas `tags` e `excluido_em`. O app do PC (`jogos_app.py`) ignora `tags`, mas **mostra os jogos da lixeira** do celular como se fossem normais, porque não conhece `excluido_em`. Esvazie a lixeira no celular antes de levar o banco para o PC.

## Detalhes

- Banco: SQLite em `files/jogos.db`, mesmas colunas do PC (`zerado`, `sem_fim`, `dropado`…). Status é um só por jogo.
- Capas: `files/capas/*.jpg`, redimensionadas para 420 px de largura.
- Importar substitui a biblioteca inteira; o app guarda a anterior como `jogos-anterior.db` (na pasta interna do app).
- Capas que nenhum jogo usa são apagadas na abertura do app.
- `allowBackup=false`: se desinstalar o app, os dados vão junto — **exporte antes**.

## Ideias para depois

- Ícone com a arte do mascote (já aplicado; o ícone temático monocromático ainda usa o coelho simplificado).
- Subir o repositório no GitHub (o `.gitignore` já deixa `jogos.db`, `capas/` e `pin.txt` de fora).
