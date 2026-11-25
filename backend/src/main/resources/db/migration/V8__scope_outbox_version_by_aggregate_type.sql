-- A versão é única por agregado, e agregado é tipo + id. A reserva de estoque (StockReservation)
-- usa o id do pedido como id do agregado; com a restrição da V5 (só aggregate_id), o primeiro
-- evento dela (versão 0) colidiria com o OrderCreated (versão 0) do mesmo pedido.

ALTER TABLE command.outbox DROP CONSTRAINT uq_outbox_aggregate_version;
ALTER TABLE command.outbox
    ADD CONSTRAINT uq_outbox_aggregate_version
        UNIQUE (aggregate_type, aggregate_id, aggregate_version);
