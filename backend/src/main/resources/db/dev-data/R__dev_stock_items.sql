-- Estoque de demonstração, aplicado só no perfil dev (spring.flyway.locations em application-dev.yml).
-- Não sobrescreve quantidades nem preços já existentes; só dá nome a produtos que ainda usam o SKU
-- como nome (V10).
INSERT INTO command.stock_items (sku, name, currency, unit_price, available) VALUES
    ('SKU-001', 'Caneca de cerâmica',        'BRL',  19.90, 500),
    ('SKU-002', 'Fone de ouvido Bluetooth',  'BRL', 100.00, 200),
    ('SKU-003', 'Caderno pontilhado A5',     'BRL',  49.90, 100),
    ('SKU-004', 'Mochila para notebook',     'BRL', 249.00,  50),
    ('SKU-005', 'Kit de adesivos',           'BRL',   9.90,   5)
ON CONFLICT (sku) DO UPDATE SET name = EXCLUDED.name
    WHERE command.stock_items.name = command.stock_items.sku;
