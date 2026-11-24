# Catálogo de erros da API

Todos os erros seguem a **RFC 9457 (Problem Details)**, com `Content-Type: application/problem+json`. O campo `type` aponta para a seção correspondente deste documento. Erros genéricos do HTTP (401, 403, 404 de rota, 405, JSON malformado) usam `type: about:blank`.

Campos sempre presentes:

| Campo | Descrição |
|---|---|
| `type` | URI do tipo de erro (seções abaixo) |
| `title` | Resumo estável do tipo de erro |
| `status` | Status HTTP |
| `detail` | Explicação desta ocorrência, segura para exibir |
| `instance` | Caminho da requisição |
| `correlationId` | Mesmo valor do header `X-Correlation-Id`; use-o ao reportar problemas |

```json
{
  "type": "https://github.com/caiodassumpcao/orderflow/blob/main/docs/api/problems.md#order-state-conflict",
  "title": "Operation not allowed in the current order status",
  "status": 409,
  "detail": "Cannot ship an order in status CREATED",
  "instance": "/api/v1/orders/01a20f13-6a05-73f7-9b74-d102e42e88ab/ship",
  "correlationId": "4f1c2b7e-8a43-4d0e-9a51-2b9e6f0c1d77",
  "currentStatus": "CREATED"
}
```

## validation-error

**400.** A requisição não passou na validação de formato (corpo, headers ou parâmetros de rota). O campo extra `errors` lista cada problema, para que o cliente associe a mensagem ao campo:

```json
"errors": [
  {"field": "items[0].quantity", "message": "must be greater than or equal to 1"},
  {"field": "items[0].sku", "message": "must have 3 to 32 uppercase letters, digits or hyphens"}
]
```

Corrija os campos e reenvie.

## invalid-order

**422.** O formato está correto, mas o pedido viola uma regra de negócio que o esquema não expressa (SKU repetido, moeda inexistente, itens em moedas diferentes). O `detail` descreve a regra.

## idempotency-key-reused

**422.** O `Idempotency-Key` já foi usado por este cliente com outro conteúdo. Gere uma chave nova para um pedido novo; para repetir o pedido original, reenvie exatamente o mesmo corpo.

## order-not-found

**404.** O pedido não existe ou pertence a outro cliente. Os dois casos têm a mesma resposta de propósito, para não revelar pedidos de terceiros.

## order-state-conflict

**409.** A operação não é permitida no estado atual do pedido (ex.: enviar um pedido ainda não confirmado, cancelar um pedido já enviado). O campo extra `currentStatus` traz o estado atual.

## concurrent-modification

**409.** Outra requisição alterou o pedido ao mesmo tempo (conflito de versão). Releia o estado e repita a operação se ainda fizer sentido.
