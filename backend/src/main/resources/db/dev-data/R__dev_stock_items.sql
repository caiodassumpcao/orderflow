-- Estoque de demonstração, aplicado só no perfil dev (spring.flyway.locations em application-dev.yml).
-- Não sobrescreve quantidades já existentes.
INSERT INTO command.stock_items (sku, currency, unit_price, available) VALUES
    ('SKU-001', 'BRL',   19.90, 500),
    ('SKU-002', 'BRL',  100.00, 200),
    ('SKU-003', 'BRL',   49.90, 100),
    ('SKU-004', 'BRL',  249.00,  50),
    ('SKU-005', 'BRL',    9.90,   5)
ON CONFLICT (sku) DO NOTHING;
