-- Histórico de movimentações de estoque.
--
-- Até aqui o schema guardava só o ÚLTIMO tipo de movimentação em
-- estoque_t (tipo_movimentacao, data_movimentacao), o que não permite
-- reconstruir a linha do tempo exigida pela TELA 09 da especificação de
-- telas. Esta tabela passa a ser o livro-razão: uma linha por alteração
-- de saldo, com o saldo antes e depois, para auditoria.

CREATE TYPE origem_movimentacao_enum AS ENUM (
    'CADASTRO',
    'AJUSTE_MANUAL',
    'PEDIDO',
    'DEVOLUCAO',
    'EXCLUSAO_PRODUTO'
);

CREATE TABLE movimentacao_t(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    estoque_id UUID NOT NULL REFERENCES estoque_t(id) ON DELETE CASCADE,
    tipo tipo_movimentacao_enum NOT NULL,
    origem origem_movimentacao_enum NOT NULL DEFAULT 'AJUSTE_MANUAL',
    -- Quantidade que entrou ou saiu. Positiva sempre; o sinal vem do tipo.
    quantidade DOUBLE PRECISION NOT NULL CHECK (quantidade >= 0),
    -- Snapshots do saldo, para mostrar a evolução sem recalcular a partir de estoque_t.
    saldo_anterior DOUBLE PRECISION NOT NULL CHECK (saldo_anterior >= 0),
    saldo_posterior DOUBLE PRECISION NOT NULL CHECK (saldo_posterior >= 0),
    descricao VARCHAR(255),
    -- NULL quando o usuário foi removido (ON DELETE SET NULL): o histórico
    -- precisa sobreviver à exclusão de conta sem virar dado órfão.
    usuario_id UUID REFERENCES usuario_t(pessoa_id) ON DELETE SET NULL,
    data_movimentacao TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Consulta quente: histórico de um estoque em ordem cronológica.
CREATE INDEX idx_movimentacao_estoque_data ON movimentacao_t(estoque_id, data_movimentacao DESC);
CREATE INDEX idx_movimentacao_usuario ON movimentacao_t(usuario_id);

-- Recria o livro-razão dos estoques que já existiam. Sem isso o histórico
-- nasceria vazio e as telas de estoque ficariam sem linha do tempo para os
-- dados atuais. O 'ENTRADA' inicial é a única leitura honesta possível: não
-- existe informação anterior preservada sobre como o saldo nasceu.
INSERT INTO movimentacao_t (
    estoque_id,
    tipo,
    origem,
    quantidade,
    saldo_anterior,
    saldo_posterior,
    descricao,
    usuario_id,
    data_movimentacao
)
SELECT
    e.id,
    e.tipo_movimentacao,
    'CADASTRO'::origem_movimentacao_enum,
    e.quantidade,
    0,
    e.quantidade,
    'Saldo inicial registrado na migração do histórico',
    NULL,
    e.data_movimentacao
FROM estoque_t e;
