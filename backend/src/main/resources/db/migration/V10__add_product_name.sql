-- Catálogo (Fase 6): nome do produto, exibido na criação de pedidos e nas telas de operação.
-- Produtos já cadastrados recebem o próprio SKU como nome até serem renomeados.

ALTER TABLE command.stock_items ADD COLUMN name varchar(120);

UPDATE command.stock_items SET name = sku WHERE name IS NULL;

ALTER TABLE command.stock_items
    ALTER COLUMN name SET NOT NULL,
    ADD CONSTRAINT ck_stock_items_name CHECK (length(btrim(name)) BETWEEN 1 AND 120);

COMMENT ON COLUMN command.stock_items.name IS 'Nome de exibição do produto';
