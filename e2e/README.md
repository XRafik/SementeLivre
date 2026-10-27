# Testes E2E web — Semente Livre

Testes de ponta a ponta com [Playwright](https://playwright.dev/docs/intro) que navegam pelo
**front-app** (proprietário, `:3000`) e pelo **front-site** (catálogo público, `:3001`) contra o
**backend real** (`:8080`) com Postgres de verdade.

Nada é mockado: cada teste cria o próprio produtor, produto e estoque pela API do backend
(`support/api.ts`) com e-mail e CPF únicos. Por isso os testes rodam em paralelo, em qualquer
ordem e sem precisar de banco limpo. A única interceptação de rede é a dos testes de
"offline", que derrubam as chamadas à API para simular o backend fora do ar.

## Como rodar localmente

Todos os comandos partem da raiz do repositório. O backend fica rodando, então use dois
terminais.

**Terminal 1 — banco e backend**

```bash
# Postgres só para os testes (porta 5434, separado do banco de dev)
docker run -d --name semente_livre_e2e_db -p 5434:5432   -e POSTGRES_USER=devuser -e POSTGRES_PASSWORD=devpassword -e POSTGRES_DB=e2edb postgres:15
# nas próximas vezes: docker start semente_livre_e2e_db

# Backend apontando para esse banco (o Flyway cria o schema e o seeder)
cd backend
./mvnw -DskipTests package
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5434/e2edb PEDIDO_PUBLICO_LIMITE_POR_IP=1000 PEDIDO_PUBLICO_LIMITE_POR_EMAIL=1000   java -jar target/backend-0.0.1-SNAPSHOT.jar
```

**Terminal 2 — frontends e testes**

```bash
# Build dos frontends (o Playwright sobe os dois com `next start`)
(cd front-app && npm ci && npm run build)
(cd front-site && npm ci && npm run build)

cd e2e
npm ci
npm run install:browsers   # só na primeira vez
npm test                   # tudo
npm run test:app           # só front-app
npm run test:site          # só front-site
npm run report             # relatório HTML da última execução
```

Se os frontends já estiverem rodando (ex.: `npm run dev`), o Playwright reaproveita.
Variáveis: `API_URL`, `APP_URL`, `SITE_URL` trocam os endereços padrão.

## No CI

`.github/workflows/e2e.yml` roda a suíte em todo PR/push para `developer` e `main` que mexa em
`backend/`, `front-app/`, `front-site/` ou `e2e/`. Sobe Postgres como service, o backend a
partir do jar, os dois frontends em build de produção e publica o relatório HTML, os
traces/vídeos de falha e o log do backend como artefatos.

## O que está coberto

| CDU | Arquivo | Cenários |
| --- | --- | --- |
| Auth | `tests/app/auth.spec.ts` | cadastro (sucesso, validação, e-mail duplicado), login (sucesso, senha errada, campos vazios), logout |
| Sessão / offline | `tests/app/sessao.spec.ts` | rota protegida, sessão após reload, token inválido, refresh silencioso, backend fora do ar na abertura e durante o uso, login sem conexão |
| Pedido | `tests/app/pedido.spec.ts` | registrar (reserva estoque), confirmar, cancelar (devolve estoque), validações, quantidade acima do estoque, pedido inexistente |
| Relatório | `tests/app/relatorio.spec.ts` | estoque e pedidos: prévia, histórico no backend, download de PDF (`%PDF-`) e CSV (conteúdo conferido), exportação desabilitada sem dados, relatório gerado antes da lista carregar (**fixme**, ver abaixo) |
| Notificação | `tests/app/notificacao.spec.ts` | pedido gera notificação não lida, contador no sino, abrir leva ao pedido, marcar todas como lidas |
| Produto | `tests/app/produto.spec.ts` | cadastro com upload de foto e estoque inicial, validações |
| Propriedade / comunidade | `tests/app/propriedade.spec.ts` | cadastro em comunidade existente, solicitar nova comunidade, validações (**fixme**, ver abaixo) |
| Catálogo público | `tests/site/catalogo.spec.ts` | listagem, busca, filtro, detalhe do produto, pedido público chegando ao produtor, oferta sem negociação |
| Erros do site | `tests/site/erros.spec.ts` | produto inexistente, catálogo indisponível, pedido sem dados, estoque insuficiente recusado pelo backend |

## Resultado da primeira execução e ajustes para a Fase 4

Primeira execução local: **39 passaram, 4 marcados como fixme** (propriedade/comunidade e a corrida do histórico de relatórios).

1. **Propriedades e comunidades não chegam ao backend.** O front-app grava as duas só no
   `localStorage` (`lib/db.ts`), então a comunidade do seeder nunca aparece e outra sessão não vê a
   propriedade. O backend também só aceitava `logradouroId`, sem endpoint para criar o endereço.
   Corrigido no **PR #161**; ao entrar no `developer`, remover o `test.fixme` de
   `propriedade.spec.ts` (rodando contra o branch do #161 os 3 testes passam).
2. **PDF/CSV do front-app são gerados no navegador** (`lib/pdf.ts` e Blob). Os endpoints
   `/relatorios/{estoque,pedidos}/{pdf,csv}` do backend não são usados por nenhuma tela; os testes
   validam o arquivo que o usuário de fato baixa. Na Fase 4, decidir se a exportação passa a usar
   o backend (uma fonte só para os números) ou se esses endpoints saem.
3. **Relatório some do histórico** quando é gerado antes da lista inicial de `/relatorios` chegar
   (corrida em `useRelatorios`; aparecia como falha intermitente). Corrigido no **PR #162**; o
   teste que reproduz a corrida fica em `fixme` até ele entrar.
4. **App travado em "Carregando sessão..."** ao renovar um token vencido: o React embutido no
   Next 16.2.6 quebra (`Cannot read properties of null (reading 'querySelector')`) ao trocar a tela
   de carregamento pelo conteúdo. Aparecia como falha intermitente de
   `sessao.spec.ts › access token vencido...` (10 em 80 execuções com 8 workers; 0 em 80 no Next
   16.4.0). Corrigido no **PR #167**.
5. **`GET /relatorios` e `GET /estoques` devolvem os registros de todos os proprietários**: o
   front-app filtra no navegador pelo `proprietarioId` (ver comentários em `lib/relatorioApi.ts` e
   `lib/catalogoApi.ts`). Na Fase 4, restringir no backend como foi feito com os
   dados pessoais no #98.
6. **Rate limit do pedido público** (10 pedidos por IP a cada 10 min) derruba suítes que fazem
   muitos pedidos; o CI sobe o backend com `PEDIDO_PUBLICO_LIMITE_POR_IP`/`POR_EMAIL` altos.
