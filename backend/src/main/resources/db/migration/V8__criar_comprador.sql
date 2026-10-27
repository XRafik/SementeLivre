CREATE TABLE comprador_t (
    id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(150) NOT NULL,
    telefone VARCHAR(20)
);

ALTER TABLE pedido_t
    ADD COLUMN comprador_id UUID REFERENCES comprador_t(id) ON DELETE RESTRICT;

CREATE INDEX idx_pedido_comprador ON pedido_t(comprador_id);