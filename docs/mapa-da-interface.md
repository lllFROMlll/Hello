# Mapa da Interface — Blér (UI Nova)

Documento de referência para a fase de **integração**: cada elemento da UI aponta para a função real que executa. Inventário completo das funcionalidades em `.kilo/plans/1788228423257-retorno-integracao-ui.md`.

Legenda de estado:
- `MOCK` — elemento visual pronto, sem ligação real.
- `LIGADO` — já conectado à função real.
- `SEM FUNÇÃO` — decorativo, comportamento a decidir pelo dono.

Estratégia atual: a UI da tela de chat é a **própria `TelaDeChat` em `MainActivity.kt`**, restilizada no visual neon da referência (`Blér chat corrigido.png`), usando componentes de `app/src/main/java/com/meuagente/app/ui/`:
- `TemaBler.kt` — cores, gradientes e tokens.
- `ComponentesChatBler.kt` — `TopoChatBler`, `BotaoCircularNeon`, `ChipData` (não usado), `BolhaMensagem`, `BarraDeEntrada`, `FormaBolhaComCauda`, `LogoBler` (não usado nesta tela).
- `TelaChatBler.kt` — **fora de uso** (casca antiga reprovada; entry volta a ser `AppPrincipal()`).

Ícones: `ic_voltar.xml`, `ic_mais_opcoes.xml` (novos); `ic_mic`, `ic_send` (existentes).

---

## Tela 1 — Chat (`TelaDeChat` em MainActivity.kt)

| # | Elemento | Componente | Aparência (fiel à imagem) | Função | Implementação | Estado |
|---|----------|------------|---------------------------|--------|---------------|--------|
| 1 | Botão ‹ (sup. esquerdo) | `BotaoCircularNeon` borda roxa | Círculo com chevron esquerdo | Abre gaveta lateral: histórico de conversas, nova conversa, fixar/excluir, configurações | `estadoGaveta.open()` (MainActivity.kt:270, 616–713) | LIGADO |
| 2 | Botão "..." (sup. direito) | `BotaoCircularNeon` borda roxa | Círculo com três pontos | — | — | SEM FUNÇÃO (dono definirá) |
| 3 | Linha neon roxa | Canvas no `TopoChatBler` | Linha horizontal fina roxa | Decorativo | — | LIGADO (visual) |
| 4 | Bolha do usuário | `BolhaMensagem(enviada=true)` | Direita, cauda direita, borda azul→ciano, hora | Mensagens "você" do Room | `items(mensagens)` na LazyColumn (MainActivity.kt:779+) | LIGADO |
| 5 | Bolha do agente | `BolhaMensagem(enviada=false)` | Esquerda, cauda esquerda, borda roxo→azul, hora | Mensagens "agente" do Room | idem | LIGADO |
| 6 | "agente está digitando..." | Texto na lista | Cinza discreto | Indicador de carregamento | `carregando` | LIGADO |
| 7 | Campo de texto | `BarraDeEntrada` (pílula borda roxa) | Placeholder "Digite uma mensagem..." | Digitar mensagem | `textoDigitado` (MainActivity.kt:273) | LIGADO |
| 8 | Botão microfone | `BotaoCircularNeon` borda roxa, dentro da pílula | Círculo com `ic_mic` | Comando de voz imersivo | permissão → `iniciarComandoVoz()` (MainActivity.kt:524, 826–833) | LIGADO |
| 9 | Botão enviar | círculo gradiente ciano→verde, avião `ic_send` | Fora da pílula, ao lado | Enviar mensagem | `enviarMensagem(texto, conversaAtualId)` (MainActivity.kt:422) | LIGADO |
| 10 | Revisão de transcrição | Transcrição pronta vai direto ao campo de diálogo (sem campo extra) | `textoPronto` → `textoDigitado` (MainActivity.kt) | LIGADO |
| 11 | Pesquisa na internet | IA pede `[BUSCAR: termos]`; app pesquisa em cascata (DuckDuckGo → Bing → Mojeek → SearXNG → Wikipedia), abre as páginas e reinjeta os resultados na conversa; indicador "🌐 Buscando na internet..." | `PesquisadorWeb.buscar` (PesquisadorWeb.kt) + fluxo em `enviarMensagem` (MainActivity.kt) | LIGADO |

Fluxos preservados: gaveta lateral (histórico/nova conversa/fixar/excluir/configurações), diálogo de exclusão, overlay `ImersaoVoz` (GRAVANDO/TRANSCREVENDO), navegação para `TelaConfiguracoes`.

---

## Tela 2 — Comando de voz imersivo (`ImersaoVoz.kt`, restilizada do vídeo)

Referência visual: `RecorteVoiceBlér.mp4` (frames analisados; margens do recorte não fazem parte do design).

| # | Elemento | Aparência | Função | Estado |
|---|----------|-----------|--------|--------|
| 1 | Orbe luminosa | Esfera branco→ciano→azul com manchas magenta, pulso lento (1400ms) | Toque = parar gravação → `pararComandoVoz` (MainActivity.kt:517) | LIGADO |
| 2 | Anéis concêntricos | 2 tracejados verde-água + 1 rosa/roxo, expandem/contragem com a intensidade da voz | Feedback visual do microfone (`intensidadeVoz`) | LIGADO |
| 3 | Cartão de legenda | Pílula escura borda teal; transcrição entre aspas, trecho reconhecido em ciano, fade 650ms | Mostrar `textoParcialVoz` ao vivo (nativo flui; IA mostra "escutando...") | LIGADO |
| 4 | Equalizador | 7 barras ciano/verde reativas ao áudio | Feedback de captação (`intensidadeVoz`) | LIGADO |
| 5 | Seta ‹ no topo | Botão circular roxo sobre o véu do topo | Sair da imersão e voltar ao chat principal → `cancelarComandoVoz` (MainActivity.kt:300) | LIGADO |
| 6 | Entrada/saída | Slide + fade suaves (550/480ms) | Transição sem corte brusco | LIGADO |

Nesta tela a seta ‹ NÃO abre a gaveta — só volta ao chat. Fora da imersão, ‹ abre a gaveta (comportamento contextual confirmado pelo dono).

---

## Tela 3 — Gaveta "Menu e Conversas" (drawer, restilizada de configuraçõesBlér)

| # | Elemento | Função | Implementação | Estado |
|---|----------|--------|---------------|--------|
| 1 | Espaço do topo | Reservado para o logo (futuro) — liso | Spacer | MOCK |
| 2 | Divisórias neon | Decorativas (magenta→ciano com glow) | `DivisoriaNeonBler` | LIGADO (visual) |
| 3 | Cartão "Nova conversa" | Criar conversa e fechar gaveta | `criarNovaConversa` (MainActivity.kt:319) | LIGADO |
| 4 | Seção "CONVERSAS / Recentes" | Só aparece quando há conversas | `cartoesConversa.isNotEmpty()` | LIGADO |
| 5 | Cartão de conversa (ativo) | Rim ciano + "Ativa agora • HH:mm" | `abrirConversa` (MainActivity.kt:335) | LIGADO |
| 6 | Cartão de conversa (inativo) | Prévia da última mensagem + hora relativa ("Ontem", "Segunda"...) | query `listarCartoesDeConversa` (AgenteDao) | LIGADO |
| 7 | Estrela (ic_pin) | Fixar/desafixar (máx. 5) | `fixarConversa` (MainActivity.kt:362) | LIGADO |
| 8 | Lixeira (ic_trash) | Excluir com confirmação | `excluirConversa` (MainActivity.kt:387) + AlertDialog | LIGADO |
| 9 | Rodapé "Configurações / Preferências e conta" | Abrir configurações | `aoAbrirConfig` → `TelaConfiguracoes` | LIGADO |

## Telas pendentes (aguardando imagens do dono)
- Logo da gaveta (PNG sem fundo, entrará no futuro)
- Menu "..." (função a definir)
