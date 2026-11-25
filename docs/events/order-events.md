# Eventos do contexto de pedidos — contrato v1

Os eventos são gravados na tabela `command.outbox` na mesma transação que altera o pedido (ver [ADR-0003](../adr/0003-transactional-outbox.md)). O relay da outbox os publica no tópico `order.events`, com `aggregateId` como chave de partição (ordem garantida por pedido no tópico). Veja [`docs/messaging.md`](../messaging.md) para retry, DLQ e a ordem do lado do consumer.

## Envelope

Cada linha da outbox guarda os campos do envelope em colunas e o payload em `jsonb`. Publicado, o evento tem esta forma:

```json
{
  "eventId": "01a20f13-6b11-7c2e-8f0a-5d2c9e7b4a10",
  "type": "OrderCreated",
  "version": 1,
  "aggregateId": "01a20f13-6a05-73f7-9b74-d102e42e88ab",
  "aggregateVersion": 0,
  "occurredAt": "2026-11-24T19:20:31.512Z",
  "correlationId": "4f1c2b7e-8a43-4d0e-9a51-2b9e6f0c1d77",
  "payload": {}
}
```

| Campo | Coluna | Descrição |
|---|---|---|
| `eventId` | `id` | UUIDv7 único; consumers usam para deduplicar |
| `type` | `event_type` | Nome do evento |
| `version` | `event_version` | Versão do contrato do payload |
| `aggregateId` | `aggregate_id` | Id do pedido |
| `aggregateVersion` | `aggregate_version` | Versão do pedido depois do evento: 0 na criação, crescente e única por pedido. Consumers aplicam só eventos mais novos do que o último que viram, o que os torna tolerantes a eventos fora de ordem |
| `occurredAt` | `occurred_at` | Quando o fato aconteceu (UTC) |
| `correlationId` | `correlation_id` | Id da requisição que originou o evento (pode ser nulo) |
| `payload` | `payload` | Conteúdo específico do evento (abaixo) |

As colunas `position` (sequencial) e `topic` não são publicadas: definem a ordem e o destino da publicação.

Valores monetários são **strings decimais com duas casas** (`"139.80"`), para não perder precisão em consumers que usam ponto flutuante.

## OrderCreated (v1)

Pedido criado, aguardando reserva de estoque.

```json
{
  "orderId": "01a20f13-6a05-73f7-9b74-d102e42e88ab",
  "customerId": "f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
  "currency": "BRL",
  "total": "139.80",
  "items": [
    {"sku": "SKU-001", "quantity": 2, "unitPrice": "19.90"},
    {"sku": "SKU-002", "quantity": 1, "unitPrice": "100.00"}
  ]
}
```

Consumidores previstos: inventário (reserva), projector (modelo de leitura).

## OrderConfirmed (v1)

Pedido confirmado depois da reserva de estoque.

```json
{"orderId": "01a20f13-6a05-73f7-9b74-d102e42e88ab"}
```

## OrderShipped (v1)

Pedido enviado.

```json
{"orderId": "01a20f13-6a05-73f7-9b74-d102e42e88ab"}
```

## OrderCancelled (v1)

Pedido cancelado. `previousStatus` indica se havia estoque reservado a liberar (`STOCK_RESERVED`); `reason` é opcional.

```json
{
  "orderId": "01a20f13-6a05-73f7-9b74-d102e42e88ab",
  "previousStatus": "CREATED",
  "reason": "Changed my mind"
}
```

## Evolução dos contratos

- Adicionar um campo opcional é compatível e mantém a versão.
- Remover ou renomear campos, ou mudar tipos ou semântica, exige uma nova versão (`version: 2`) publicada em paralelo até todos os consumers migrarem.
- Consumers devem ignorar campos desconhecidos.
