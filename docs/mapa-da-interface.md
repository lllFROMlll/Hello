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
| 10 | Revisão de transcrição | `RodapeDeVoz` (só estado PRONTO) | Campo de texto editável + botão enviar | Revisar/enviar transcrição de voz | `enviarTranscricao()` (MainActivity.kt:567) | LIGADO |

Fluxos preservados: gaveta lateral (histórico/nova conversa/fixar/excluir/configurações), diálogo de exclusão, overlay `ImersaoVoz` (GRAVANDO/TRANSCREVENDO), navegação para `TelaConfiguracoes`.

---

## Telas pendentes (aguardando imagens do dono)
- Configurações
- Comando de voz imersivo
- Menu "..." (função a definir)
