# Mensageria

Como os eventos saem do banco, chegam ao Kafka e são consumidos com segurança. Decisões em [ADR-0003](adr/0003-transactional-outbox.md) (outbox), [ADR-0004](adr/0004-kafka-kraft.md) (broker) e [ADR-0007](adr/0007-non-blocking-retry.md) (retry). Contratos dos eventos em [`events/`](events/order-events.md).

```mermaid
flowchart LR
    API[Comando HTTP] -->|mesma transação| DB[(command.orders<br/>command.outbox)]
    DB -->|OutboxRelay<br/>ordem de position| T{{order.events}}
    T --> C[Consumer]
    C -->|falha| R0{{order.events.consumer.retry-0}}
    R0 -->|falha| R1{{...retry-1}}
    R1 -->|falha| DLQ{{order.events.consumer.dlq}}
    C -->|poison pill| DLQ
    C -->|mesma transação| PE[(processed_events<br/>+ efeitos)]
```

## Publicação (relay)

- O caso de uso grava agregado e eventos em `command.outbox` na mesma transação.
- `OutboxRelay` roda a cada `orderflow.messaging.relay.interval` (500 ms) e publica as linhas pendentes em ordem de `position`, até `batch-size` (100) por lote.
- **Uma instância por vez:** um advisory lock transacional do PostgreSQL elege quem publica. Duas instâncias publicando lotes em paralelo poderiam inverter eventos de um mesmo pedido.
- **Marcação só após confirmação:** `published_at` só é gravado depois do `ack` do broker (`acks=all`, produtor idempotente). Na primeira falha o lote para e só o prefixo confirmado é marcado; a próxima varredura recomeça do primeiro pendente.
- **Mensagem:** chave = `aggregateId` (todos os eventos de um pedido na mesma partição, em ordem), valor = envelope JSON do contrato.
- **Garantia:** at-least-once. Se o relay cair entre a confirmação do broker e a marcação, o evento é publicado de novo; os consumers deduplicam pelo `eventId`.
- **Broker indisponível:** o envio desiste em `max.block.ms` (10 s), o evento continua pendente e a API segue funcionando.

## Tópicos

| Tópico | Quem cria | Conteúdo |
|---|---|---|
| `order.events` | aplicação (`OrderTopics`), na subida | eventos do agregado Order |
| `order.events.<consumer>.retry-<n>` | aplicação, ao registrar o consumer | novas tentativas do consumer |
| `order.events.<consumer>.dlq` | aplicação, ao registrar o consumer | mensagens que o consumer desistiu de processar |

O broker não cria tópicos sozinho (`auto.create.topics.enable=false`). Partições: `orderflow.messaging.topics.partitions` (3). Réplicas: `KAFKA_REPLICATION_FACTOR`, obrigatório fora de dev (use 3 em produção).

Os tópicos de retry e DLQ levam o nome do consumer porque cada consumer group precisa dos seus: se o projector e o inventário compartilhassem `order.events.retry-0`, um receberia as novas tentativas do outro.

## Consumo

Todo consumer segue a mesma receita:

```java
@Component
class OrderProjector {

  static final String NAME = "projector";

  private final IdempotentEventProcessor processor;
  private final DeadLetters deadLetters;

  @OrderflowRetryableTopic(retryTopicSuffix = ".projector.retry", dltTopicSuffix = ".projector.dlq")
  @KafkaListener(topics = OrderTopics.ORDER_EVENTS, groupId = NAME)
  void on(ConsumerRecord<String, String> record) {
    processor.process(NAME, record.value(), envelope -> {
      // efeitos do consumer; rodam na mesma transação que a marcação em processed_events
    });
  }

  @DltHandler
  void onDeadLetter(
      ConsumerRecord<String, String> record,
      @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String reason) {
    deadLetters.record(NAME, record.topic(), record.key(), reason);
  }
}
```

- **Idempotência:** `IdempotentEventProcessor` grava `(eventId, consumer)` em `command.processed_events` e executa o handler na mesma transação. Reentregas são descartadas; se o handler falhar, nada fica gravado e a mensagem vai para o retry.
- **Retry:** backoff exponencial em tópicos próprios (`attempts` 4, `initial-delay-ms` 1000, `multiplier` 2.0, `max-delay-ms` 10000, em `orderflow.messaging.retry.*`). Enquanto uma mensagem espera, as seguintes da partição continuam sendo processadas.
- **Poison pill:** mensagem que não é um envelope válido lança `PoisonMessageException` e vai direto para a DLQ, sem retry.
- **Ordem:** como o retry não bloqueia a partição, um consumer pode receber `OrderCancelled` antes de um `OrderCreated` que está em retry. **Todo consumer precisa tolerar eventos fora de ordem** (ex.: projeções versionadas, ignorar transições que já aconteceram). Ver ADR-0007.
- **Rastreabilidade:** o `correlationId` do envelope vai para o MDC, então os logs do consumer têm o mesmo id da requisição HTTP de origem.

## Métricas

| Métrica | Tipo | Uso |
|---|---|---|
| `orderflow.outbox.pending` | gauge | eventos ainda não publicados; alerta se crescer continuamente |
| `orderflow.outbox.published` | counter | eventos confirmados pelo broker |
| `orderflow.outbox.publish.failures` | counter | falhas de publicação (broker fora, timeout) |
| `orderflow.messaging.processed{consumer}` | counter | eventos processados pela primeira vez |
| `orderflow.messaging.duplicates{consumer}` | counter | reentregas descartadas |
| `orderflow.messaging.dead.letters{consumer}` | counter | mensagens enviadas à DLQ; alerta a cada incremento |

Expostas em `/actuator/prometheus`. Dashboards e alertas entram na Fase 9.

## Operação

- Ver tópicos e mensagens: Kafka UI em `http://localhost:8081`.
- Reprocessar uma DLQ: corrigir a causa e republicar as mensagens no tópico principal; a deduplicação por `eventId` torna a republicação segura. Uma ferramenta própria para isso fica para a Fase 9.
