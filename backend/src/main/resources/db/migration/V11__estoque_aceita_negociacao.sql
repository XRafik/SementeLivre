-- O produtor decide se aceita negociar a oferta.
--
-- Aceitando, quem pede pelo site pode propor outro tipo (ex.: troca numa
-- oferta de venda). Não aceitando, o pedido fica preso ao tipo da oferta.
-- As ofertas que já existem continuam aceitando, como antes.

ALTER TABLE estoque_t ADD COLUMN aceita_negociacao BOOLEAN NOT NULL DEFAULT TRUE;
