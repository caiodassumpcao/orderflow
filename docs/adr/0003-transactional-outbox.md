---
status: accepted
date: 2026-11-24
---

# ADR-0003: Transactional Outbox em vez de dual-write

## Contexto e problema

Cada comando de pedidos muda o estado do agregado no PostgreSQL e precisa avisar outros contextos (inventário, projeções) por eventos no Kafka. Gravar no banco e publicar no broker são duas operações em sistemas diferentes, sem transação distribuída entre eles.

Como garantir que um evento é publicado **se e somente se** a mudança de estado foi confirmada?

## Fatores de decisão

- Nenhum evento para mudança desfeita (rollback) e nenhuma mudança confirmada sem evento.
- A API não pode falhar nem ficar lenta porque o broker está indisponível.
- Ordem dos eventos por pedido preservada.
- Sem coordenador de transação distribuída (2PC/XA).

## Opções consideradas

1. **Dual-write:** gravar no banco e publicar no Kafka na mesma requisição.
2. **Transactional Outbox:** gravar o evento numa tabela na mesma transação do agregado; um relay publica depois.
3. **Change Data Capture** (ex.: Debezium lendo o WAL).

## Decisão

Opção 2: **Transactional Outbox**.

- O caso de uso grava o agregado, a chave de idempotência e as linhas de `command.outbox` numa única transação (`UnitOfWork`). Os adapters exigem uma transação em andamento (`Propagation.MANDATORY`), então nenhum deles grava fora dela por engano.
- Cada linha tem o envelope do evento (`eventId` UUIDv7, tipo, versão, `aggregateId`, `occurredAt`, `correlationId`) e o payload versionado em `jsonb`. Os contratos estão em `docs/events/`.
- Cada linha recebe uma `position` sequencial gerada pelo banco (`IDENTITY`). Nem `created_at` (o `now()` do PostgreSQL é o início da transação e pode empatar) nem o UUIDv7 (aleatório dentro do mesmo milissegundo) garantem ordem; para um mesmo pedido, `position` segue a ordem de commit, porque o controle otimista de versão impede duas transações concorrentes no mesmo agregado.
- Um índice parcial em `position` (`published_at IS NULL`) atende o relay (`OutboxRelay`, Fase 2): ele lê as linhas pendentes em ordem de `position`, publica no tópico gravado na linha com `aggregateId` como chave e marca `published_at` só depois da confirmação do broker. Detalhes em `docs/messaging.md`.

### Consequências

- **Positivas:** atomicidade entre estado e evento garantida pelo próprio PostgreSQL; a API não depende da disponibilidade do Kafka; o histórico de eventos fica auditável no banco.
- **Negativas:** a entrega é **at-least-once** (o relay pode publicar de novo se cair entre publicar e marcar), então os consumers precisam ser idempotentes pelo `eventId` (Fase 2); há latência entre o commit e a publicação; a tabela cresce e precisa de política de limpeza.
- **Neutras:** a ordem por pedido depende de o relay publicar as linhas de um mesmo `aggregateId` em ordem de `position`.

## Prós e contras das opções

### Dual-write

- Bom, porque é simples e sem latência extra.
- Ruim, porque uma falha entre as duas escritas deixa banco e broker inconsistentes, em qualquer ordem que se escolha.
- Ruim, porque acopla a disponibilidade da API à do Kafka.

### Change Data Capture

- Bom, porque não exige código de relay e lê direto do log do banco.
- Ruim, porque acrescenta infraestrutura (Kafka Connect, Debezium) e acopla o contrato dos eventos ao formato das tabelas, a menos que se use também uma outbox.

## Mais informações

Implementação: `orders.application.OrderCommandService`, `orders.adapter.messaging.OutboxOrderEventPublisher` e `shared.messaging.OutboxWriter`. Testes de integração em `OrderWriteModelIT` provam que JPA e JDBC compartilham a transação e que uma falha desfaz o agregado e o evento juntos.
