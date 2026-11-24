-- Schemas do CQRS: escrita (command) e leitura (query).
-- Migrations são imutáveis depois de aplicadas: crie uma nova versão para alterações.

CREATE SCHEMA command;
COMMENT ON SCHEMA command IS 'Modelo de escrita: agregados, outbox e processed_events';

CREATE SCHEMA query;
COMMENT ON SCHEMA query IS 'Modelo de leitura: projeções desnormalizadas atualizadas por eventos';
