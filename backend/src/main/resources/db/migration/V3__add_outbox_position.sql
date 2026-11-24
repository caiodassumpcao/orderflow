-- Ordem determinística da outbox. created_at (now() = início da transação) pode empatar e o UUIDv7
-- só é ordenável entre milissegundos distintos; a posição sequencial é a ordem de gravação.
-- Para um mesmo pedido ela reflete a ordem de commit, porque o controle otimista de versão impede
-- duas transações concorrentes de confirmar mudanças no mesmo agregado.

ALTER TABLE command.outbox
    ADD COLUMN position bigint GENERATED ALWAYS AS IDENTITY;

ALTER TABLE command.outbox
    ADD CONSTRAINT uq_outbox_position UNIQUE (position);

COMMENT ON COLUMN command.outbox.position IS
    'Ordem de gravação; o relay publica as pendentes em ordem crescente de position';

-- O relay lê as pendentes em ordem de position.
DROP INDEX command.ix_outbox_unpublished;
CREATE INDEX ix_outbox_unpublished ON command.outbox (position) WHERE published_at IS NULL;
