-- Inventário (Fase 4): produtos com preço de catálogo e reservas de estoque por pedido.

CREATE TABLE command.stock_items (
    sku        varchar(32)   PRIMARY KEY,
    currency   varchar(3)    NOT NULL,
    unit_price numeric(12,2) NOT NULL,
    available  integer       NOT NULL,
    reserved   integer       NOT NULL DEFAULT 0,
    version    bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ck_stock_items_sku CHECK (sku ~ '^[A-Z0-9][A-Z0-9-]{2,31}$'),
    CONSTRAINT ck_stock_items_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_stock_items_unit_price CHECK (unit_price > 0),
    CONSTRAINT ck_stock_items_quantities CHECK (available >= 0 AND reserved >= 0)
);
COMMENT ON TABLE command.stock_items IS
    'Produtos: preço de catálogo, unidades livres (available) e reservadas para pedidos (reserved)';

CREATE TABLE command.stock_reservations (
    order_id         uuid        PRIMARY KEY,
    status           varchar(32) NOT NULL,
    rejection_reason varchar(32),
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    version          bigint      NOT NULL,
    CONSTRAINT ck_stock_reservations_status
        CHECK (status IN ('RESERVED', 'REJECTED', 'RELEASED', 'FULFILLED',
                          'CANCELLED_BEFORE_RESERVATION')),
    CONSTRAINT ck_stock_reservations_reason
        CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL))
);
COMMENT ON TABLE command.stock_reservations IS
    'Reserva de estoque por pedido (uma por pedido; decidida uma única vez)';

CREATE TABLE command.stock_reservation_lines (
    order_id    uuid          NOT NULL REFERENCES command.stock_reservations (order_id),
    line_number integer       NOT NULL,
    sku         varchar(32)   NOT NULL,
    quantity    integer       NOT NULL,
    unit_price  numeric(12,2) NOT NULL,
    PRIMARY KEY (order_id, line_number),
    CONSTRAINT ck_stock_reservation_lines_quantity CHECK (quantity > 0)
);
COMMENT ON TABLE command.stock_reservation_lines IS 'Itens pedidos, com o preço informado no pedido';
