-- Versão do agregado em cada evento (0 na criação, crescente por agregado). Permite aos consumers
-- aplicar só o que é mais novo, já que o retry não bloqueante não preserva a ordem (ADR-0007).

ALTER TABLE command.outbox ADD COLUMN aggregate_version bigint;

-- Linhas existentes: a ordem de gravação por agregado define a versão.
UPDATE command.outbox AS o
   SET aggregate_version = numbered.version
  FROM (SELECT id, row_number() OVER (PARTITION BY aggregate_id ORDER BY position) - 1 AS version
          FROM command.outbox) AS numbered
 WHERE o.id = numbered.id;

ALTER TABLE command.outbox ALTER COLUMN aggregate_version SET NOT NULL;
ALTER TABLE command.outbox
    ADD CONSTRAINT ck_outbox_aggregate_version CHECK (aggregate_version >= 0);
ALTER TABLE command.outbox
    ADD CONSTRAINT uq_outbox_aggregate_version UNIQUE (aggregate_id, aggregate_version);

COMMENT ON COLUMN command.outbox.aggregate_version IS
    'Versão do agregado depois do evento; crescente e única por agregado';
