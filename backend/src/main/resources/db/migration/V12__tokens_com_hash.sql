-- #206: refresh token e token de recuperação de senha passam a ser guardados
-- só como SHA-256 (hex). Os que já existem são convertidos no lugar, então
-- ninguém perde a sessão nem o link de recuperação em andamento.
-- sha256() é nativa do PostgreSQL desde a versão 11.
UPDATE refresh_token_t
SET token = encode(sha256(convert_to(token, 'UTF8')), 'hex');

UPDATE token_recuperacao_senha_t
SET token = encode(sha256(convert_to(token, 'UTF8')), 'hex');
