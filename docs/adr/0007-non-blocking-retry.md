---
status: accepted
date: 2026-11-24
---

# ADR-0007: Retry não bloqueante por consumer, com DLQ

## Contexto e problema

Um consumer pode falhar ao processar um evento por um motivo passageiro (banco indisponível, conflito de versão) ou permanente (bug, dado inesperado). Repetir na hora costuma falhar de novo; desistir cedo perde o evento. Mensagens que nunca serão válidas (poison pills) não devem ser repetidas.

Como repetir com intervalo crescente, sem perder mensagens e sem parar o consumo inteiro por causa de uma mensagem?

## Fatores de decisão

- Falhas passageiras se resolvem sozinhas, sem intervenção.
- Uma mensagem problemática não pode bloquear as demais indefinidamente.
- Mensagens irrecuperáveis ficam guardadas e visíveis (métrica e alerta), para correção e reprocessamento.
- Vários consumer groups no mesmo tópico, com políticas independentes.

## Opções consideradas

1. **Retry bloqueante** na própria partição (`DefaultErrorHandler` com backoff) e DLQ ao final.
2. **Retry não bloqueante** em tópicos de retry com atraso (`@RetryableTopic` do Spring Kafka) e DLQ ao final.

## Decisão

Opção 2, encapsulada em `@OrderflowRetryableTopic`:

- Tópicos `<tópico>.<consumer>.retry-<n>` com backoff exponencial (4 tentativas no total, 1 s, ×2, até 10 s, configuráveis) e `<tópico>.<consumer>.dlq` depois da última tentativa.
- Tópicos com o nome do consumer, porque consumer groups diferentes não podem compartilhar tópicos de retry.
- `PoisonMessageException` vai direto para a DLQ.
- Cada mensagem na DLQ incrementa `orderflow.messaging.dead.letters{consumer}` e gera log de erro sem o payload.

### Consequências

- **Positivas:** uma mensagem com falha não segura a partição; falhas passageiras se resolvem com espaçamento crescente; a DLQ guarda o que precisa de intervenção.
- **Negativas:** **a ordem por agregado deixa de ser garantida no consumer.** Se `OrderCreated` entra em retry, um `OrderCancelled` posterior do mesmo pedido pode ser processado antes. Todo consumer precisa tolerar eventos fora de ordem (projeções versionadas, transições idempotentes ou espera explícita pelo evento anterior). Isso é requisito de projeto para o projector (Fase 3) e o inventário (Fase 4).
- **Neutras:** mais tópicos por consumer (2 a 4); todos criados pela aplicação.

## Prós e contras das opções

### Retry bloqueante

- Bom, porque mantém a ordem por partição.
- Ruim, porque uma mensagem problemática para a partição inteira durante todo o backoff, e todos os pedidos daquela partição esperam.
- Ruim, porque um backoff longo se aproxima do `max.poll.interval.ms` e provoca rebalanceamento.

## Mais informações

Guia de uso e métricas em `docs/messaging.md`. Teste de integração em `MessagingIT` (retry, DLQ e poison pill com Kafka real).
