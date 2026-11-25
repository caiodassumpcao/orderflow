-- Visão de administrador (Fase 5): listagem de todos os pedidos com paginação keyset.
-- Parcial: linhas sem OrderCreated projetado (customer_id nulo) nunca aparecem nas consultas.
CREATE INDEX ix_order_summaries_created
    ON query.order_summaries (created_at DESC, order_id DESC)
    WHERE customer_id IS NOT NULL;
