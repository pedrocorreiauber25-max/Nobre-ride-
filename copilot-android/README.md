# Nobre Ride Copilot — Android

Primeira fundação nativa do Radar de Viagens.

## Incluído
- Aplicação Android nativa em Kotlin.
- Identidade gráfica própria Nobre Ride Copilot, integrada no ecrã inicial.
- Pedido de autorização Android para captura do ecrã.
- Serviço em primeiro plano com notificação persistente e visível.
- OCR local com Google ML Kit para ler texto apresentado no ecrã.
- Botão para parar a captura.
- Nenhuma ação automática de aceitar/rejeitar viagens.

## Ainda por implementar
- Reconhecimento estruturado e validado de preço, distância e duração nas ofertas Uber/Bolt.
- Classificação de rentabilidade e histórico automático.
- Interface de histórico e regras configuráveis.
- Testes reais no Galaxy Tab A9 com Android 16.

Esta fundação não é ainda um APK pronto para uso em estrada. A captura depende da autorização do utilizador e das regras do Android. Esta versão não grava áudio nem vídeo.

## Compilar
Abrir a pasta `copilot-android` no Android Studio, com JDK 17 e Android SDK 35, e executar a tarefa `assembleDebug`.
