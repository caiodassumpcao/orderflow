---
status: accepted
date: 2026-11-25
---

# ADR-0006: Paginação keyset nas listagens grandes

## Contexto e problema

A listagem de pedidos de um cliente cresce sem limite e é a consulta mais frequente. Paginação com `OFFSET` obriga o banco a ler e descartar todas as linhas anteriores à página pedida, e inserções concorrentes fazem itens pularem ou se repetirem entre páginas.

## Fatores de decisão

- Custo constante por página, independente da profundidade.
- Nenhum item repetido ou perdido quando pedidos novos chegam durante a navegação.
- Contrato de API que permita mudar a estratégia sem quebrar clientes.

## Opções consideradas

1. **`OFFSET`/`LIMIT`** com número de página.
2. **Keyset (seek)** com cursor opaco.

## Decisão

Opção 2.

- Ordem estável e total: `created_at DESC, order_id DESC`. O `order_id` desempata pedidos criados no mesmo instante.
- Próxima página: `WHERE customer_id = ? AND (created_at, order_id) < (?, ?) ORDER BY created_at DESC, order_id DESC LIMIT n + 1`. A linha extra só indica se há próxima página.
- Índice `(customer_id, created_at DESC, order_id DESC)` atende a busca e a ordenação sem sort.
- O cliente recebe `nextCursor` (base64url de `createdAt|orderId`) e o devolve no parâmetro `cursor`. O formato é opaco e pode mudar; um cursor adulterado é rejeitado com 400.
- Página de 1 a 100 itens (padrão 20).

### Consequências

- **Positivas:** custo por página constante; navegação consistente com inserções concorrentes; o cursor opaco preserva a liberdade de mudar a implementação.
- **Negativas:** não há "pular para a página N" nem total de páginas; navegar para trás exigiria um cursor reverso (não implementado, sem necessidade nas telas previstas).
- **Neutras:** o filtro por status usa o mesmo índice e filtra as linhas; um índice específico, se necessário, sai do estudo de performance da Fase 8, com medições antes e depois.

## Prós e contras das opções

### `OFFSET`/`LIMIT`

- Bom, porque permite pular para qualquer página e mostrar o total.
- Ruim, porque o custo cresce com a profundidade, e itens pulam ou se repetem quando a lista muda entre uma página e outra.

## Mais informações

Implementação em `orders.adapter.persistence.JdbcOrderReadModel` e `orders.adapter.web.PageCursorCodec`. A comparação com `OFFSET` (com `EXPLAIN ANALYZE` e massa de dados) entra em `docs/db/performance.md` na Fase 8.
