# financeapp

Aplicação de finanças pessoais em Spring Boot 4 / Java 21.

## Stack

| Camada | Escolha |
| --- | --- |
| Runtime | Java 21 (Temurin) |
| Framework | Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Actuator, Validation) |
| Banco (dev/teste) | H2 em memória, `MODE=PostgreSQL` |
| Banco (postgres/prod) | PostgreSQL 18 via Docker Compose |
| Migrations | Flyway |
| Testes | JUnit 5, Spring Boot Test, Testcontainers, JaCoCo |
| Build | Maven Wrapper (`mvnw`) |

## Rodando

Não existe perfil padrão: o JAR, ou a aplicação iniciada sem perfil, só sobe com banco
e `JWT_SECRET` configurados. O `./mvnw spring-boot:run` ativa o perfil `dev` sozinho;
pela IDE, defina o perfil ativo (`dev`) na configuração de execução.

### Modo rápido (H2, sem Docker) — perfil `dev`

```bash
./mvnw spring-boot:run
```

- API: http://localhost:8080
- Console do H2: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:financeapp`, usuário `sa`, senha vazia)
- Health: http://localhost:8080/actuator/health

O console do H2 e o segredo JWT de desenvolvimento só existem no perfil `dev`, e o H2
não é empacotado no JAR final.

### Com PostgreSQL — perfil `postgres`

Copie `.env.example` para `.env` e preencha `DB_PASSWORD` e `JWT_SECRET` (os dois são
obrigatórios; gere o segredo com `openssl rand -base64 48`).

```bash
docker compose up -d
```

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

O banco fica acessível só na própria máquina (`127.0.0.1`). O Adminer (cliente web do
banco, em http://localhost:8081) é opcional:

```bash
docker compose --profile tools up -d
```

### Com PostgreSQL descartável (Testcontainers)

```bash
./mvnw spring-boot:test-run
```

Sobe um Postgres efêmero e o derruba ao encerrar. Requer Docker rodando.

## Testes

```bash
./mvnw test
```

Verificação completa, com relatório de cobertura em `target/site/jacoco/index.html`
(o build falha abaixo de 80% de cobertura):

```bash
./mvnw verify
```

Os testes usam o perfil `test` (`src/test/resources/application-test.properties`), com H2
em memória; toda classe de teste que sobe o Spring leva `@ActiveProfiles("test")`.
`PostgresIntegrationTest` roda o fluxo principal e os cenários de concorrência contra um
PostgreSQL real e é pulado quando não há Docker (no CI ele roda). Antes de publicar uma
mudança no banco ou no controle de tentativas, rode o `verify` com o Docker ligado.

## Banco de dados e migrations

O Flyway é a única fonte de verdade do schema; o Hibernate roda com
`ddl-auto=validate` e apenas confere se as entidades batem com as tabelas.

- Migrations ficam em `src/main/resources/db/migration`.
- Nome do arquivo: `V<n>__descricao.sql` (ex.: `V3__cria_tabela_conta.sql`).
- Escreva SQL compatível com PostgreSQL — o H2 do perfil `dev` roda em modo de
  compatibilidade, então a mesma migration vale nos dois bancos.
- **Nunca edite uma migration já aplicada.** O Flyway guarda o checksum e falha
  no boot se ele mudar; crie uma nova versão.

## API de usuários

| Método | Rota | Acesso | O que faz |
| --- | --- | --- | --- |
| POST | `/users` | público | Cadastro (`name`, `email`, `password`, `payDay`) |
| POST | `/auth/login` | público | Devolve `accessToken` (JWT, 1 hora) |
| GET | `/users/me` | token | Dados da própria conta |
| PUT | `/users/me` | token | Altera `name` e `payDay` |
| PUT | `/users/me/email` | token + `currentPassword` | Troca o e-mail e invalida os tokens antigos |
| PUT | `/users/me/password` | token + `currentPassword` | Troca a senha e invalida os tokens antigos |
| POST | `/users/me/delete` | token + `currentPassword` | Apaga a conta |

O token vai no header `Authorization: Bearer <accessToken>`. Não existe rota por id
nem listagem: cada usuário só enxerga e altera a própria conta. Depois de trocar
e-mail ou senha é preciso fazer login de novo.

## Tela provisória

Com a aplicação no ar, http://localhost:8080 abre uma tela de desenvolvimento, em HTML,
CSS e JavaScript puros (`src/main/resources/static`), sem build:

- `/` — abas "Entrar" e "Criar conta";
- `/dashboard.html` — painel com os dados reais da conta (cartões "conta") e cartões de
  rascunho com números fictícios (cartões "exemplo"), para decidir o que o produto vai ter.

Os números fictícios ficam todos em `static/js/sample-data.js`. Quando uma funcionalidade
virar real, o cartão passa a ler da API e a entrada correspondente sai desse arquivo.

O token fica no `sessionStorage` (some ao fechar a aba), as páginas não têm script nem
estilo inline e são servidas com `Content-Security-Policy: default-src 'self'`. É uma tela
provisória: o front definitivo deve reavaliar onde guardar o token.

Ao editar um arquivo de `static/` com a aplicação rodando, rode `./mvnw resources:resources`
(ou recompile pela IDE) para a mudança aparecer.

## Segurança

- Senhas gravadas só como hash BCrypt (`password_hash`); nunca aparecem em respostas ou logs.
- Senha nova: mínimo de 8 caracteres e máximo de 72 bytes. E-mail único, normalizado em minúsculas.
- Login responde o mesmo 401 para e-mail inexistente, senha errada e conta bloqueada.
- 5 senhas erradas seguidas (no login ou na confirmação de senha atual) bloqueiam a conta
  por 15 minutos. Cada tentativa é reservada no banco antes de a senha ser conferida, então
  requisições paralelas não testam mais senhas do que o limite.
- Limite por IP nas rotas públicas: 10 logins por minuto e 10 cadastros por hora
  (`app.security.rate-limit.*`); acima disso, 429. Endereços IPv6 do mesmo /64 contam como
  um cliente, e a memória do limitador é limitada (janelas vencidas são removidas e há um
  teto de clientes acompanhados; o excedente divide um contador comum).
- O token é aceito só se não expirou, foi emitido por e para esta API (`iss`, `aud`) e
  pertence a uma conta ativa com as credenciais atuais.
- Do Actuator só o `health` é exposto, sem detalhes.
- Eventos de segurança (login, senha incorreta, bloqueio, troca de e-mail/senha, exclusão,
  limite excedido) vão para o logger `security.audit`, com id da conta ou IP e nunca senhas.
- Erros no formato `ProblemDetail`: 400 (validação, com `errors` por campo), 401,
  403 (senha atual incorreta), 409 (e-mail em uso), 429 (limite de requisições).

### Limitações conhecidas

- **O cadastro e a troca de e-mail revelam se um e-mail já existe** (409). Só dá para
  esconder isso com confirmação de e-mail, que ainda não existe.
- **O bloqueio é por conta**: quem conhece um e-mail consegue travar aquela conta por 15
  minutos errando a senha de propósito.
- **O limite por IP fica na memória de uma instância.** Com mais de uma instância, ou atrás
  de um proxy, o limite principal deve ficar no proxy/gateway (e é preciso configurar
  `server.forward-headers-strategy` para a aplicação enxergar o IP real).
- Não há confirmação de e-mail, recuperação de senha, refresh token nem logout no servidor.

## Estrutura

```
src/main/java/com/finance/app/financeapp/
├── config/         # SecurityConfig, PersistenceConfig
├── dto/            # entradas e saídas da API (a entidade nunca é serializada)
├── entities/       # User
├── exceptions/     # exceções de domínio
├── repositories/   # UserRepository
├── resources/      # UserResource, AuthResource e tratamento de erros
├── security/       # PasswordGuard, TokenService, TokenOwnerValidator, RateLimitFilter
└── services/       # UserService, AuthService
src/main/resources/
├── application.properties           # config comum (sem perfil padrão)
├── application-dev.properties       # H2
├── application-postgres.properties  # PostgreSQL
└── db/migration/                    # migrations Flyway
```
