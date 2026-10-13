# Semente Livre - Backend

## 1. Descrição do Projeto
Este é o serviço de backend do sistema Semente Livre. A aplicação foi construída como uma API RESTful utilizando Spring Boot e gerencia a persistência de dados em um banco PostgreSQL rodando em container. O controle de versionamento das tabelas do banco de dados é automatizado através do Flyway.

## 2. Tecnologias Utilizadas
- **Java 17**
- **Spring Boot 4.1.1**
- **Maven Wrapper** (Gerenciamento de dependências integrado)
- **PostgreSQL 15**
- **Spring Data JPA** (Persistência de dados via Hibernate)
- **Flyway** (Versionamento de banco de dados)
- **Docker e Docker Compose**
- **Lombok** (Redução de código boilerplate)
- **Bean Validation** (Validação de dados de entrada)

## 3. Pré-requisitos
Para rodar este projeto na sua máquina, você precisará ter instalado:
- **Java 17** ou superior.
- **Docker** (O Docker Desktop já inclui o Docker Compose nativamente).

> **Nota:** O projeto utiliza o Maven Wrapper (`mvnw` / `mvnw.cmd`). Portanto, não é necessário instalar o Maven separadamente no seu sistema operacional. O próprio script se encarrega de baixar e usar a versão correta da ferramenta.

## 4. Como executar o projeto

Compose sobe PostgreSQL e aplicação juntos:

```bash
docker compose up --build -d
```

O serviço `app` espera PostgreSQL ficar saudável antes de iniciar. Aplicação fica disponível em `http://localhost:8080`; PostgreSQL fica disponível em `localhost:5433`.

Verifique serviços e logs:

```bash
docker compose ps
docker compose logs app
```

Pare ambiente:

```bash
docker compose down
```

Para iniciar só PostgreSQL e executar aplicação no host:

```bash
docker compose up -d db
```

- Linux/macOS: `./mvnw spring-boot:run`
- Windows: `mvnw.cmd spring-boot:run`

### Testes

Testes de integração usam Testcontainers com PostgreSQL 15. Docker precisa estar em execução.

```bash
mvn test
```

Ou Maven Wrapper:

- Linux/macOS: `./mvnw test`
- Windows: `mvnw.cmd test`

Teste inicia contexto Spring, aplica migrations Flyway e persiste/consulta grafo de `Comunidade` e `Propriedade` em PostgreSQL real.

## 5. Swagger/OpenAPI e CORS

Com aplicação rodando, acesse:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Swagger UI direto: `http://localhost:8080/swagger-ui/index.html`
- Documento JSON: `http://localhost:8080/v3/api-docs`

Em ambiente `dev`, CORS permite somente:

- `http://localhost:3000` (`front-app`)
- `http://localhost:3001` (`front-site`)

Métodos permitidos: `GET`, `POST`, `PUT`, `PATCH`, `DELETE` e `OPTIONS`. Origem externa não recebe autorização CORS.

Todos os endpoints aparecem no Swagger, agrupados pelo `@Tag` de cada controller
(Autenticação, Comunidades, Pedidos, Catálogo público...). Os schemas são gerados dos
DTOs que os controllers usam; não registre schema escrito à mão no `OpenApiConfig`,
porque ele substitui o gerado e fica desatualizado.

Para chamar rotas protegidas pelo Swagger UI: execute `POST /auth/login`, copie o
`accessToken` da resposta, clique em **Authorize** e cole o token (sem o prefixo
`Bearer`). Rotas de administração exigem a conta de admin.

`CorsOpenApiIntegrationTest` garante que todo endpoint tem grupo, que o esquema JWT
está no documento e que o CORS libera só as origens dos frontends.

Teste preflight:

```bash
curl -i -X OPTIONS http://localhost:8080/v3/api-docs -H "Origin: http://localhost:3000" -H "Access-Control-Request-Method: GET"
```

## 6. Configuração do Banco de Dados

PostgreSQL e aplicação rodam no Docker Compose. As credenciais de desenvolvimento estão definidas em docker-compose.yml.
O profile `dev` conecta à porta interna `5432` quando aplicação roda no Compose e à porta `5433` quando aplicação roda no host. DBeaver e DataGrip usam `localhost:5433`.
PostgreSQL e aplicação rodam no Docker Compose. As credenciais de desenvolvimento estão definidas em docker-compose.yml.
O profile `dev` conecta à porta interna `5432` quando aplicação roda no Compose e à porta `5433` quando aplicação roda no host. DBeaver e DataGrip usam `localhost:5433`.

## 6. Profiles de Ambiente (dev / prod)
A aplicação está configurada para separar as propriedades de desenvolvimento e produção. O profile padrão ativo ao rodar o projeto localmente é o `dev`.

- `application-dev.yml`: Configurações exclusivas da máquina local (conecta ao container Docker e exibe logs SQL).
- `application-prod.yml`: Configurações para o servidor de produção (receberá variáveis de ambiente no deploy).

Se for necessário testar ou forçar a execução de um profile específico via terminal, utilize:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## 7. Versionamento do Banco (Flyway)
As migrações do banco de dados (criação e alteração de tabelas) são gerenciadas pelo Flyway. Os scripts SQL estão localizados no diretório:
`src/main/resources/db/migration/`

As migrações `V1__init.sql` e `V2__create_domain_tables.sql` são executadas automaticamente pelo Flyway na ordem correta ao iniciar Spring Boot.

### Fluxo de Migrações
O desenvolvimento de novas migrações deve seguir a nomenclatura sequencial, utilizando sub-versões para evitar conflitos em branches paralelas (ex: `V1_1__...`, `V1_2__...`). O escopo base do sistema (Fase 1) foi organizado em:
- **Schema:** Tabelas, constraints, enums e triggers de auditoria.
- **Otimização:** Indexação de chaves estrangeiras e colunas de busca frequente.
- **Seeders:** Dados mínimos iniciais (Admin, Comunidade e Produtos) para ambiente de homologação e testes.

### Idempotência e Testes
A execução das migrações é nativamente idempotente. O Flyway gerencia o controle de estado através da tabela `flyway_schema_history`, garantindo que um script rodado com sucesso não seja executado novamente.
Para garantir a integridade do schema, o projeto conta com **TestContainers**. Durante as execuções de testes, o framework provisiona uma instância limpa e descartável do PostgreSQL e executa todas as migrações do zero (Clean Run), atestando a validade dos scripts de forma isolada.

### Política de Rollback
Na versão Community do Flyway utilizada no projeto, rollbacks automatizados (scripts `U__`) não são suportados nativamente. Em caso de falha crítica durante o desenvolvimento local (ex: erro de *Checksum* por alteração de script antigo ou falha sintática estrutural), a política de rollback consiste em:
1. Conectar ao banco de dados local via CLI (`psql`) ou SGBD (DBeaver).
2. Limpar o schema atual: `DROP SCHEMA public CASCADE; CREATE SCHEMA public;`
3. Corrigir o arquivo `V__` que gerou o conflito.
4. Reiniciar a aplicação (`./mvnw spring-boot:run`) para recriar todo o estado limpo do banco a partir dos scripts originais.

## 8. Como verificar se está funcionando

Na pasta `backend`, verifique serviços e logs:

```bash
docker compose ps
docker compose logs app
```

- `db` deve estar `healthy`.
- `app` deve exibir `Started BackendApplication`.
- `flyway_schema_history`, `teste_inicial` e tabelas de domínio devem existir.


## 9. Estrutura do Projeto
Uma visão geral da organização dos arquivos de configuração e código fonte:

```text
backend/
├── .mvn/                        # Configurações do Maven Wrapper
├── src/
│   ├── main/
│   │   ├── java/                # Código fonte Java
│   │   └── resources/
│   │       ├── db/migration/    # Scripts SQL do Flyway
│   │       ├── application.yml        # Configuração padrão
│   │       ├── application-dev.yml    # Configurações locais (Docker)
│   │       ├── application-test.yml   # Configurações dos testes
│   │       └── application-prod.yml   # Configurações de produção
│   └── test/                    # Testes e fixtures com Testcontainers
├── Dockerfile                   # Imagem da aplicação
├── docker-compose.yml           # Aplicação + PostgreSQL
├── mvnw                         # Script do Maven para Linux/Mac
├── mvnw.cmd                     # Script do Maven para Windows
├── pom.xml                      # Dependências do projeto
└── readme.md                    # Documentação do projeto
```

## 10. Arquitetura de Pacotes (Backend)
Todo o código-fonte fica sob o pacote base `com.sementelivre.backend` (sempre em
minúsculo, seguindo a convenção Java). Cada camada tem uma responsabilidade única
e um `package-info.java` documentando seu propósito.

```text
com.sementelivre.backend
├── config/         # Beans do Spring, CORS, OpenAPI/Swagger (@Configuration)
├── controller/     # Endpoints REST (@RestController). Sem regra de negócio
├── dto/            # Objetos de transferência (entrada/saída da API)
├── entity/         # Entidades JPA (@Entity)
│   └── enums/      # Enums de domínio (ex: StatusComunidade)
├── exception/      # Exceções de negócio + GlobalExceptionHandler
├── repository/     # Acesso a dados (Spring Data JPA)
├── security/       # Autenticação, autorização, filtros e encoder de senha
├── service/        # Regra de negócio (@Service)
└── util/           # Utilitários transversais sem estado
```

### Convenções de nomenclatura
Aplicadas de forma idêntica em todos os domínios:

| Camada        | Sufixo / padrão                    | Exemplo                        |
|---------------|------------------------------------|--------------------------------|
| Entidade      | Nome do domínio (singular)         | `Comunidade`                   |
| Tabela        | `snake_case` + sufixo `_t`         | `comunidade_t`                 |
| DTO           | `<Dominio>DTO`                     | `ComunidadeDTO`                |
| DTO entrada   | `<Dominio>RequestDTO`              | `UsuarioRequestDTO`            |
| DTO saída     | `<Dominio>ResponseDTO`            | `UsuarioResponseDTO`           |
| Repository    | `<Dominio>Repository`              | `ComunidadeRepository`         |
| Service       | `<Dominio>Service`                 | `ComunidadeService`            |
| Controller    | `<Dominio>Controller`              | `ComunidadeController`         |
| Enum          | Nome descritivo (em `entity.enums`)| `StatusComunidade`             |
| Exceção       | `<Motivo>Exception`                | `ResourceNotFoundException`    |

- Pacotes: sempre minúsculo (`com.sementelivre.backend`).
- Classes: `PascalCase`. Métodos e atributos: `camelCase`.
- Endpoints REST: substantivo no plural (`/comunidades`, `/propriedades`).

### Interfaces base para CRUD
Para manter os domínios uniformes, existem dois contratos base:

- **`repository.BaseRepository<T, ID>`** — estende `JpaRepository` e é anotado com
  `@NoRepositoryBean`. Os repositórios concretos estendem esta interface em vez de
  `JpaRepository` diretamente, centralizando futuros métodos comuns.

  ```java
  public interface ComunidadeRepository extends BaseRepository<Comunidade, UUID> { }
  ```

- **`service.CrudService<REQ, RES, ID>`** — contrato comum das operações de CRUD,
  separando o DTO de entrada (`REQ`) do DTO de saída (`RES`).

  ```java
  @Service
  public class ComunidadeService
          implements CrudService<ComunidadeDTO, ComunidadeDTO, UUID> {
      // criar, buscarPorId, listar, atualizar, deletar
  }
  ```

> Não há um controller base: os `@RestController` variam demais em rotas, códigos
> de status e documentação OpenAPI para uma superclasse comum agregar valor.