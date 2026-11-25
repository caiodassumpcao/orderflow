# Eventos do contexto de inventário — contrato v1

Publicados pelo inventário em `inventory.events` em resposta ao `OrderCreated`, pelo mesmo caminho dos eventos de pedidos (outbox → relay → Kafka). O envelope é o mesmo descrito em [`order-events.md`](order-events.md).

| Campo do envelope | Valor |
|---|---|
| `aggregateId` | id do **pedido** (a reserva é única por pedido); também é a chave da mensagem |
| `aggregateVersion` | versão da reserva: `0` para a decisão (`StockReserved` ou `StockRejected`) |
| `type` | `StockReserved` ou `StockRejected` |

Consumidor atual: a saga de pedidos (`order-saga`), que confirma ou cancela o pedido.

Os schemas (`stock-reserved.v1`, `stock-rejected.v1`) e os exemplos ficam em [`schemas/`](schemas/) e [`examples/`](examples/), verificados no build como descrito em [`order-events.md`](order-events.md#contratos-verificáveis).

## StockReserved (v1)

Todos os itens do pedido foram reservados ao preço de catálogo.

```json
{
  "orderId": "01a2103c-f4b5-7ecd-aa9f-eb26ff2329b2",
  "items": [
    {"sku": "SKU-003", "quantity": 2}
  ]
}
```

## StockRejected (v1)

O pedido foi recusado e nada foi reservado.

```json
{
  "orderId": "01a2103c-f4b5-7ecd-aa9f-eb26ff2329b2",
  "reason": "OUT_OF_STOCK",
  "skus": ["SKU-005"]
}
```

| `reason` | Quando |
|---|---|
| `UNKNOWN_SKU` | algum produto não existe no catálogo (verificado primeiro) |
| `PRICE_MISMATCH` | o preço unitário ou a moeda do pedido diferem do catálogo |
| `OUT_OF_STOCK` | algum produto não tem unidades livres suficientes |

`skus` lista os produtos que causaram a recusa. A saga grava no pedido cancelado o motivo `Stock rejected: <reason> (<skus>)`.

## O que não vira evento

Liberar o estoque de um pedido cancelado e dar baixa no estoque de um pedido enviado são efeitos internos do inventário, sem evento próprio: ninguém depende deles hoje.
