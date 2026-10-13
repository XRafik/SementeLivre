-- Pedidos feitos pelo site público.
--
-- Quem pede pelo site não tem conta: o pedido fica sem usuário solicitante e
-- os dados de contato vão para o comprador. O e-mail do comprador é para onde
-- vai a cópia do pedido.

ALTER TABLE pedido_t ALTER COLUMN usuario_solicitante_id DROP NOT NULL;

ALTER TABLE comprador_t ADD COLUMN email VARCHAR(254);
