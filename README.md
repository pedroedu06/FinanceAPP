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

### Modo rápido (H2, sem Docker) — perfil `dev`, o padrão

```bash
./mvnw spring-boot:run
```

- API: http://localhost:8080
- Console do H2: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:financeapp`, usuário `sa`, senha vazia)
- Health: http://localhost:8080/actuator/health

### Com PostgreSQL — perfil `postgres`

```bash
docker compose up -d
```

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

- Adminer (cliente web do banco): http://localhost:8081
- Credenciais e porta saem do `.env` (copie de `.env.example`).
- `JWT_SECRET` é obrigatório neste perfil (mínimo 32 caracteres); sem ele a aplicação não sobe.

### Com PostgreSQL descartável (Testcontainers)

```bash
./mvnw spring-boot:test-run
```

Sobe um Postgres efêmero e o derruba ao encerrar. Requer Docker rodando.

## Testes

```bash
./mvnw test
```

Verificação completa com relatório de cobertura em `target/site/jacoco/index.html`:

```bash
./mvnw verify
```

Os testes usam H2 em memória (`src/test/resources/application.properties`), então não
dependem de Docker. Para um teste de integração contra Postgres real, importe
`TestFinanceappApplication` com `@Import`.

## Banco de dados e migrations

O Flyway é a única fonte de verdade do schema; o Hibernate roda com
`ddl-auto=validate` e apenas confere se as entidades batem com as tabelas.

- Migrations ficam em `src/main/resources/db/migration`.
- Nome do arquivo: `V<n>__descricao.sql` (ex.: `V2__cria_tabela_conta.sql`).
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
| PUT | `/users/me/email` | token + `currentPassword` | Troca o e-mail |
| PUT | `/users/me/password` | token + `currentPassword` | Troca a senha e invalida os tokens antigos |
| DELETE | `/users/me` | token + `currentPassword` | Apaga a conta |

O token vai no header `Authorization: Bearer <accessToken>`. Não existe rota por id
nem listagem: cada usuário só enxerga e altera a própria conta.

## Segurança

- Senhas gravadas só como hash BCrypt (`password_hash`); nunca aparecem em respostas ou logs.
- Senha de 8 a 72 caracteres; e-mail único, normalizado em minúsculas.
- Login responde o mesmo 401 para e-mail inexistente, senha errada e conta bloqueada.
- 5 senhas erradas seguidas (no login ou na confirmação de senha atual) bloqueiam a conta por 15 minutos.
- Trocar a senha ou apagar a conta invalida os tokens já emitidos.
- Erros no formato `ProblemDetail`: 400 (validação, com `errors` por campo), 401, 403 (senha atual incorreta), 409 (e-mail em uso).

Ainda não há confirmação de e-mail, recuperação de senha nem refresh token.

## Estrutura

```
src/main/java/com/finance/app/financeapp/
├── config/         # SecurityConfig, JwtProperties, TokenOwnerValidator
├── dto/            # entradas e saídas da API (a entidade nunca é serializada)
├── entities/       # User
├── repositories/   # UserRepository
├── resources/      # UserResource, AuthResource e tratamento de erros
└── services/       # UserService, AuthService, TokenService, PasswordGuard
src/main/resources/
├── application.properties           # config comum
├── application-dev.properties       # H2
├── application-postgres.properties  # PostgreSQL
└── db/migration/                    # migrations Flyway
```
