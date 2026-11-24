-- Modelo de escrita de pedidos: agregado, itens, outbox e chaves de idempotência.
-- As restrições repetem as invariantes do domínio como última linha de defesa.

CREATE TABLE command.orders (
    id                  uuid          PRIMARY KEY,
    customer_id         varchar(64)   NOT NULL,
    status              varchar(20)   NOT NULL,
    currency            varchar(3)    NOT NULL,
    total_amount        numeric(14,2) NOT NULL,
    cancellation_reason varchar(200),
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    version             bigint        NOT NULL,
    CONSTRAINT ck_orders_status
        CHECK (status IN ('CREATED', 'STOCK_RESERVED', 'CONFIRMED', 'SHIPPED', 'CANCELLED')),
    CONSTRAINT ck_orders_currency_iso4217 CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_orders_total_not_negative CHECK (total_amount >= 0),
    CONSTRAINT ck_orders_reason_only_when_cancelled
        CHECK (cancellation_reason IS NULL OR status = 'CANCELLED')
);
COMMENT ON TABLE command.orders IS 'Agregado Order (modelo de escrita)';
COMMENT ON COLUMN command.orders.version IS 'Controle de concorrência otimista';

CREATE TABLE command.order_lines (
    order_id    uuid          NOT NULL REFERENCES command.orders (id) ON DELETE CASCADE,
    line_number integer       NOT NULL,
    sku         varchar(32)   NOT NULL,
    quantity    integer       NOT NULL,
    unit_price  numeric(12,2) NOT NULL,
    PRIMARY KEY (order_id, line_number),
    CONSTRAINT uq_order_lines_sku UNIQUE (order_id, sku),
    CONSTRAINT ck_order_lines_quantity CHECK (quantity BETWEEN 1 AND 1000),
    CONSTRAINT ck_order_lines_unit_price CHECK (unit_price > 0 AND unit_price <= 1000000)
);
COMMENT ON TABLE command.order_lines IS 'Itens do pedido, na ordem em que foram enviados';

-- Transactional Outbox: eventos gravados na mesma transação do agregado e publicados por um relay.
CREATE TABLE command.outbox (
    id             uuid         PRIMARY KEY,
    aggregate_type varchar(50)  NOT NULL,
    aggregate_id   uuid         NOT NULL,
    event_type     varchar(100) NOT NULL,
    event_version  smallint     NOT NULL,
    payload        jsonb        NOT NULL,
    correlation_id varchar(64),
    occurred_at    timestamptz  NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    published_at   timestamptz
);
COMMENT ON TABLE command.outbox IS 'Transactional Outbox; id é o eventId (UUIDv7) do envelope';

-- O relay lê só o que falta publicar, em ordem de gravação.
CREATE INDEX ix_outbox_unpublished ON command.outbox (created_at) WHERE published_at IS NULL;

CREATE TABLE command.idempotency_keys (
    customer_id     varchar(64)  NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash    char(64)     NOT NULL,
    order_id        uuid         NOT NULL REFERENCES command.orders (id),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (customer_id, idempotency_key)
);
COMMENT ON TABLE command.idempotency_keys IS
    'Idempotency-Key da criação de pedidos, por cliente; request_hash é o SHA-256 do conteúdo';
