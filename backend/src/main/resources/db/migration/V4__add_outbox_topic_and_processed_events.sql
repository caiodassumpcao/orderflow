-- Mensageria (Fase 2): tópico de destino na outbox e deduplicação dos consumers.

-- O produtor do evento decide o tópico; o relay só publica o que está na linha.
ALTER TABLE command.outbox ADD COLUMN topic varchar(249);
UPDATE command.outbox SET topic = 'order.events' WHERE aggregate_type = 'Order';
ALTER TABLE command.outbox ALTER COLUMN topic SET NOT NULL;
COMMENT ON COLUMN command.outbox.topic IS 'Tópico Kafka de destino';

-- Consumers idempotentes: cada (evento, consumer) é processado no máximo uma vez. A linha é gravada
-- na mesma transação que os efeitos do consumer, então uma reentrega é descartada com segurança.
CREATE TABLE command.processed_events (
    event_id     uuid         NOT NULL,
    consumer     varchar(100) NOT NULL,
    processed_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer)
);
COMMENT ON TABLE command.processed_events IS
    'Eventos já processados por consumer (deduplicação da entrega at-least-once)';
