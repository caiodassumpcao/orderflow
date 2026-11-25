---
status: accepted
date: 2026-11-25
---

# ADR-0008: Saga coreografada de estoque, com o preço validado pelo inventário

## Contexto e problema

Um pedido só pode ser confirmado se houver estoque, e o estoque pertence ao contexto de inventário. Os contextos conversam só por eventos (ADR-0001). Além disso, até a Fase 3 o preço unitário vinha da requisição do cliente, sem conferência: qualquer cliente podia definir o próprio preço.

Como coordenar pedido e estoque sem transação distribuída nem chamada síncrona, e de onde vem o preço de verdade?

## Fatores de decisão

- Contextos desacoplados: nenhum chama o outro, nenhum lê as tabelas do outro.
- Reserva tudo ou nada por pedido, sem vender além do estoque sob concorrência.
- Tolerância a eventos repetidos e fora de ordem (ADR-0007).
- Fechar a brecha do preço definido pelo cliente.

## Opções consideradas

1. **Saga orquestrada** por um coordenador no contexto de pedidos.
2. **Saga coreografada:** cada contexto reage aos eventos do outro.
3. **Chamada síncrona** ao inventário no `POST /orders`.

Para o preço:

A. **Catálogo no contexto de pedidos**, consultado na criação.
B. **Catálogo no inventário**, conferido na reserva.

## Decisão

**Saga coreografada (2) com o catálogo no inventário (B).**

- `OrderCreated` → o inventário trava os produtos (em ordem de SKU, sem deadlock), confere SKU, preço e moeda e quantidade, e publica `StockReserved` ou `StockRejected`. Cada pedido é decidido uma única vez.
- `StockReserved` → o pedido passa por `STOCK_RESERVED` e é confirmado (`OrderConfirmed`). `StockRejected` → o pedido é cancelado com o motivo (`OrderCancelled`).
- `OrderCancelled` → o inventário devolve o estoque reservado. Se o cancelamento chegar antes da criação, ele fica registrado e a criação é ignorada.
- `OrderShipped` → o inventário dá baixa no estoque reservado.
- Respostas para pedidos que já mudaram de rumo (ex.: cliente cancelou durante a reserva) são ignoradas pela saga; o cancelamento devolve o estoque.
- O preço enviado pelo cliente continua no pedido, mas só é aceito se for igual ao de catálogo; senão o pedido é cancelado com `PRICE_MISMATCH`.

### Consequências

- **Positivas:** nenhum acoplamento síncrono; o inventário é a fonte de verdade de estoque e preço; um preço adulterado não gera venda; cada passo é idempotente e testado com Kafka real.
- **Negativas:** a confirmação é assíncrona: o `POST` responde `CREATED` e o pedido vira `CONFIRMED` ou `CANCELLED` logo depois (menos de um segundo em condições normais). O cliente descobre um preço desatualizado só pelo cancelamento; a tela de criação (Fase 6) deve mostrar o preço de catálogo para evitar isso.
- **Neutras:** `STOCK_RESERVED` é um estado de passagem, porque a confirmação acontece na mesma transação; continua no modelo para uma etapa futura (ex.: pagamento) entre reserva e confirmação.

## Prós e contras das opções

### Saga orquestrada

- Bom, porque o fluxo fica explícito num só lugar.
- Ruim, porque cria um coordenador com estado próprio para um fluxo de dois passos; a coreografia basta e dispensa mais uma peça.

### Chamada síncrona ao inventário

- Bom, porque o cliente recebe a resposta final no `POST`.
- Ruim, porque acopla a disponibilidade da API à do inventário e contraria o ADR-0001.

### Catálogo no contexto de pedidos

- Bom, porque o preço é validado já no `POST`.
- Ruim, porque duplica o cadastro de produtos ou exige consulta síncrona a outro contexto.

## Mais informações

Contratos em `docs/events/inventory-events.md`. Testes: `StockReservationIT` (concorrência e travas), `OrderSagaIT` (fluxos confirmado, sem estoque, preço divergente e envio, com Kafka real).
