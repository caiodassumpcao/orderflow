-- Modelo de leitura de pedidos (CQRS), mantido pelo projector a partir de order.events.
-- Os eventos podem chegar fora de ordem (ADR-0007): uma linha pode nascer de um evento de status
-- antes do OrderCreated. Até lá os detalhes ficam nulos e a linha não aparece nas consultas.

CREATE TABLE query.order_summaries (
    order_id            uuid          PRIMARY KEY,
    customer_id         varchar(64),
    status              varchar(20)   NOT NULL,
    currency            varchar(3),
    total_amount        numeric(14,2),
    item_count          integer,
    cancellation_reason varchar(200),
    created_at          timestamptz,
    updated_at          timestamptz   NOT NULL,
    last_event_version  bigint        NOT NULL,
    CONSTRAINT ck_order_summaries_status
        CHECK (status IN ('CREATED', 'STOCK_RESERVED', 'CONFIRMED', 'SHIPPED', 'CANCELLED')),
    CONSTRAINT ck_order_summaries_details_complete
        CHECK ((customer_id IS NULL) = (created_at IS NULL)
           AND (customer_id IS NULL) = (currency IS NULL)
           AND (customer_id IS NULL) = (total_amount IS NULL)
           AND (customer_id IS NULL) = (item_count IS NULL))
);
COMMENT ON TABLE query.order_summaries IS 'Projeção de pedidos para listagem e detalhe';
COMMENT ON COLUMN query.order_summaries.customer_id IS 'Nulo enquanto o OrderCreated não chegou';
COMMENT ON COLUMN query.order_summaries.last_event_version IS
    'aggregateVersion do último evento aplicado; eventos mais antigos são ignorados';

-- Paginação keyset da listagem do cliente: (created_at, order_id) decrescentes.
CREATE INDEX ix_order_summaries_customer_created
    ON query.order_summaries (customer_id, created_at DESC, order_id DESC);

CREATE TABLE query.order_items (
    order_id    uuid          NOT NULL REFERENCES query.order_summaries (order_id) ON DELETE CASCADE,
    line_number integer       NOT NULL,
    sku         varchar(32)   NOT NULL,
    quantity    integer       NOT NULL,
    unit_price  numeric(12,2) NOT NULL,
    PRIMARY KEY (order_id, line_number)
);
COMMENT ON TABLE query.order_items IS 'Itens do pedido na projeção, na ordem original';
