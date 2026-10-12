-- Integridade dos dados do domínio de pessoas -- issue #133 (Dev 3 · S8).
--
-- Só leitura. Toda linha deve sair com ocorrencias = 0, exceto as marcadas
-- como (informativo). Rodar no banco de produção com:
--   psql "$DB_URL" -f integridade_pessoas.sql
-- ou, no compose de produção:
--   docker exec -i semente_livre_db_prod psql -U semente_user -d semente_prod < integridade_pessoas.sql

\echo '== Migrações (Flyway)'
SELECT version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;

\echo '== Checagens (ocorrencias deve ser 0)'
SELECT verificacao, ocorrencias FROM (
    SELECT 1 AS ordem, 'INT-01 migração com falha' AS verificacao,
           (SELECT count(*) FROM flyway_schema_history WHERE NOT success) AS ocorrencias
    UNION ALL
    SELECT 2, 'INT-02 pessoa sem tipo (nem admin nem proprietário)',
           (SELECT count(*) FROM pessoa_t p
            WHERE NOT EXISTS (SELECT 1 FROM admin_t a WHERE a.pessoa_id = p.id)
              AND NOT EXISTS (SELECT 1 FROM proprietario_t r WHERE r.pessoa_id = p.id))
    UNION ALL
    SELECT 3, 'INT-03 pessoa com os dois tipos (admin e proprietário)',
           (SELECT count(*) FROM admin_t a JOIN proprietario_t r ON r.pessoa_id = a.pessoa_id)
    UNION ALL
    SELECT 4, 'INT-04 admin/proprietário sem conta de login',
           (SELECT count(*) FROM pessoa_t p
            WHERE NOT EXISTS (SELECT 1 FROM usuario_t u WHERE u.pessoa_id = p.id))
    UNION ALL
    SELECT 5, 'INT-05 conta de login sem perfil (role)',
           (SELECT count(*) FROM usuario_t u
            WHERE NOT EXISTS (SELECT 1 FROM usuario_role_t ur WHERE ur.usuario_id = u.pessoa_id))
    UNION ALL
    SELECT 6, 'INT-06 perfil incoerente com o tipo (admin sem ROLE_ADMIN ou proprietário sem ROLE_PROPRIETARIO)',
           (SELECT count(*) FROM pessoa_t p
            WHERE (EXISTS (SELECT 1 FROM admin_t a WHERE a.pessoa_id = p.id)
                   AND NOT EXISTS (SELECT 1 FROM usuario_role_t ur JOIN role_t ro ON ro.id = ur.role_id
                                   WHERE ur.usuario_id = p.id AND ro.nome = 'ROLE_ADMIN'))
               OR (EXISTS (SELECT 1 FROM proprietario_t r WHERE r.pessoa_id = p.id)
                   AND NOT EXISTS (SELECT 1 FROM usuario_role_t ur JOIN role_t ro ON ro.id = ur.role_id
                                   WHERE ur.usuario_id = p.id AND ro.nome = 'ROLE_PROPRIETARIO')))
    UNION ALL
    SELECT 7, 'INT-07 conta de login sem senha',
           (SELECT count(*) FROM usuario_t WHERE senha_hash IS NULL OR senha_hash = '')
    UNION ALL
    SELECT 8, 'INT-08 e-mails iguais ignorando maiúsculas/espaços',
           (SELECT count(*) FROM (SELECT lower(trim(email)) FROM pessoa_t GROUP BY 1 HAVING count(*) > 1) d)
    UNION ALL
    SELECT 9, 'INT-09 e-mail gravado com maiúsculas ou espaços nas pontas',
           (SELECT count(*) FROM pessoa_t WHERE email <> lower(trim(email)))
    UNION ALL
    SELECT 10, 'INT-10 documento com caractere que não é dígito',
           (SELECT count(*) FROM pessoa_t WHERE documento !~ '^[0-9]+$')
    UNION ALL
    SELECT 11, 'INT-11 CPF/CNPJ com todos os dígitos iguais (inválido)',
           (SELECT count(*) FROM pessoa_t WHERE documento ~ '^(\d)\1+$')
    UNION ALL
    SELECT 12, 'INT-12 RG repetido ignorando maiúsculas e pontuação',
           (SELECT count(*) FROM (SELECT regexp_replace(upper(rg), '[^A-Z0-9]', '', 'g')
                                  FROM proprietario_t GROUP BY 1 HAVING count(*) > 1) d)
    UNION ALL
    SELECT 13, 'INT-13 endereço órfão (logradouro sem pessoa, comunidade ou propriedade)',
           (SELECT count(*) FROM logradouro_t l
            WHERE NOT EXISTS (SELECT 1 FROM pessoa_t p WHERE p.logradouro_id = l.id)
              AND NOT EXISTS (SELECT 1 FROM comunidade_t c WHERE c.logradouro_id = l.id)
              AND NOT EXISTS (SELECT 1 FROM propriedade_t pr WHERE pr.logradouro_id = l.id))
    UNION ALL
    SELECT 14, 'INT-14 comprador órfão (sem pedido)',
           (SELECT count(*) FROM comprador_t c
            WHERE NOT EXISTS (SELECT 1 FROM pedido_t p WHERE p.comprador_id = c.id))
    UNION ALL
    SELECT 15, 'INT-15 dados de teste qa-s8 restantes',
           (SELECT count(*) FROM pessoa_t WHERE email ILIKE 'qa-s8-%@teste.sementelivre.dev')
    UNION ALL
    SELECT 16, 'INT-16 (informativo) pessoas cadastradas',
           (SELECT count(*) FROM pessoa_t)
) checagens
ORDER BY ordem;
