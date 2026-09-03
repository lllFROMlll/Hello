# Mapa da Interface — Blér (UI Nova)

Documento de referência para a fase de **integração**: cada elemento da UI nova (casca visual) aponta para a função existente que será ligada a ele. Inventário completo das funcionalidades em `.kilo/plans/1788228423257-retorno-integracao-ui.md`.

Legenda de estado:
- `MOCK` — elemento visual pronto, sem ligação real (fase casca).
- `LIGADO` — já conectado à função real.

Arquivos da UI nova: `app/src/main/java/com/meuagente/app/ui/`
- `TemaBler.kt` — cores, gradientes e tokens de design.
- `ComponentesChatBler.kt` — `LogoBler`, `BotaoCircularNeon`, `ChipData`, `BolhaMensagem`, `BarraDeEntrada`, `FormaBolhaComCauda`.
- `TelaChatBler.kt` — composição da tela de chat (entry atual via `MainActivity.onCreate` → `setContent`).

---

## Tela 1 — Chat (`TelaChatBler.kt`)

Referência visual: `chat.Blér.jpeg` (fundo preto-azulado, header com logo, bolhas neon, barra de entrada).

| # | Elemento | ID/Componente | Aparência | Função prevista | Função existente de destino (arquivo:linha) | Estado |
|---|----------|---------------|-----------|-----------------|---------------------------------------------|--------|
| 1 | Botão voltar ‹ | `BotaoCircularNeon` (borda roxa, canto sup. esquerdo) | Círculo com chevron esquerdo | Abrir lista de conversas / voltar | Abas e conversas: `carregarConversas` (MainActivity.kt:315), `abrirConversa` (MainActivity.kt:338) | MOCK |
| 2 | Logo Blér® | `LogoBler` (vetorial: globo neon + B + texto gradiente) | Círculo neon verde→roxo com "B", texto "Blér®" gradiente verde→azul→rosa | Decorativo (identidade) | — | MOCK |
| 3 | Botão menu "..." | `BotaoCircularNeon` (borda roxa, canto sup. direito) | Círculo com três pontos | Menu da conversa: fixar, excluir, limpar | `fixarConversa` (MainActivity.kt:365), `excluirConversa` (MainActivity.kt:390) | MOCK |
| 4 | Chip de data | `ChipData` ("Hoje") | Pílula cinza-escura central | Agrupar mensagens por dia (novo recurso) | Sem destino ainda (novo) | MOCK |
| 5 | Bolha recebida | `BolhaMensagem(enviada=false)` | Esquerda, cauda esquerda, borda roxo→azul, hora dentro | Exibir mensagens da IA | `mensagens` do Room via `AgenteDao` (AgenteDao.kt) | MOCK (lista mock) |
| 6 | Bolha enviada | `BolhaMensagem(enviada=true)` | Direita, cauda direita, borda azul→ciano/roxo, hora + ✓✓ ciano | Exibir mensagens do usuário | `salvarMensagemSistema` (MainActivity.kt:463) / persistência Room | MOCK (lista mock) |
| 7 | Check duplo ✓✓ | texto dentro da bolha enviada | Ciano, junto à hora | Status de entrega (decorativo) | — | MOCK |
| 8 | Campo de texto | `TextField` na `BarraDeEntrada` | Placeholder "Digite uma mensagem..." | Digitar mensagem | `enviarMensagem` (MainActivity.kt:425), `textoPronto` (MainActivity.kt:473) | MOCK |
| 9 | Botão microfone | `BotaoCircularNeon` (borda roxa, dentro da barra) | Círculo com ícone `ic_mic` | Comando de voz imersivo | `iniciarComandoVoz` (MainActivity.kt:527), `alternarModoVoz` (MainActivity.kt:565), `cancelarComandoVoz` (MainActivity.kt:303); UI de imersão: `ImersaoVoz.kt` | MOCK |
| 10 | Botão enviar | círculo gradiente ciano→verde com avião (`ic_send`) | Círculo à direita da barra | Enviar mensagem | `enviarMensagem` (MainActivity.kt:425) → `perguntarComProvedor` (MainActivity.kt:140) | MOCK |

Fluxo de navegação previsto: Tela de Chat ↔ (voltar) Lista de Conversas ↔ (menu) Configurações → `TelaConfiguracoes.kt` (existente).

---

## Telas pendentes (aguardando imagens do dono)
- Configurações
- Comando de voz imersivo
- Lista de conversas / abas
