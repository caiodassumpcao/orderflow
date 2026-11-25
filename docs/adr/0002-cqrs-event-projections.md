---
status: accepted
date: 2026-11-25
---

# ADR-0002: CQRS com projeções por eventos

## Contexto e problema

O modelo de escrita de pedidos é normalizado e otimizado para garantir invariantes (agregado, itens, versão otimista). As telas precisam de outra coisa: listar os pedidos de um cliente com filtro e paginação, sem juntar tabelas a cada requisição, e com espaço para dashboards agregados mais adiante.

Como servir as consultas sem acoplar o modelo de escrita às necessidades das telas?

## Fatores de decisão

- Consultas rápidas e simples, com índices próprios.
- Modelo de escrita livre para evoluir sem quebrar telas.
- Usar os eventos que já existem (outbox → Kafka) em vez de criar outro mecanismo de sincronização.
- Aceitar consistência eventual, desde que visível e controlada.

## Opções consideradas

1. **Consultar o modelo de escrita** direto (`command.*`), com joins.
2. **CQRS com projeções** no schema `query`, atualizadas por um consumer dos eventos.
3. **Views materializadas** no PostgreSQL, atualizadas periodicamente.

## Decisão

Opção 2. O projector (`order-projector`) consome `order.events` e mantém `query.order_summaries` e `query.order_items`. As consultas (`GET /api/v1/orders`, `GET /api/v1/orders/{id}`) leem só o schema `query`.

- **Tolerância a ordem e repetição:** cada evento traz `aggregateVersion`. A projeção aplica o status do evento de maior versão, e o `OrderCreated` preenche os detalhes mesmo chegando depois de um evento de status; cada evento vira um único upsert atômico. A deduplicação por `eventId` vem da infraestrutura de consumers (Fase 2).
- **Linha ainda incompleta:** se um evento de status chega antes do `OrderCreated`, a linha existe sem detalhes e fica fora das consultas até ele chegar.
- **Contratos, não tabelas:** o projector depende dos contratos versionados em `docs/events/`, nunca das tabelas de escrita.

### Consequências

- **Positivas:** consultas sem joins, com índices pensados para a tela; o modelo de escrita evolui sem afetar a leitura; dá para reconstruir a projeção republicando os eventos.
- **Negativas:** consistência eventual. Logo após um comando, o `GET` pode ainda não refletir a mudança (tipicamente menos de um segundo: intervalo do relay + consumo). A resposta do próprio comando já traz o estado novo, e é ela que a interface deve usar; o estado "pendente de sincronização" é tratado na UI.
- **Neutras:** mais uma peça para operar (o consumer), com DLQ e métricas próprias.

## Prós e contras das opções

### Consultar o modelo de escrita

- Bom, porque é sempre consistente.
- Ruim, porque acopla a tabela de escrita às telas, exige joins e índices que atrapalham a escrita, e não escala para dashboards.

### Views materializadas

- Bom, porque não exige consumer.
- Ruim, porque o refresh é periódico e global (custo alto, atraso maior) e não aproveita os eventos que já existem.

## Mais informações

Implementação: `orders.application.projection`, `orders.application.query`, `orders.adapter.messaging.OrderProjectionListener`, `orders.adapter.persistence.JdbcOrderProjectionStore`. Testes: `OrderReadModelIT` (ordem de chegada qualquer) e `OrderProjectionIT` (ciclo completo com Kafka).
