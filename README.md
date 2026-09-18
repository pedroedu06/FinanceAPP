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
- Login de dev: `dev` / `dev`

### Com PostgreSQL — perfil `postgres`

```bash
docker compose up -d
```

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

- Adminer (cliente web do banco): http://localhost:8081
- Credenciais e porta saem do `.env` (copie de `.env.example`).

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

## Estrutura

```
src/main/java/com/finance/app/financeapp/
├── FinanceappApplication.java
└── config/
    └── SecurityConfig.java      # base de segurança, trocar por auth real
src/main/resources/
├── application.properties           # config comum
├── application-dev.properties       # H2
├── application-postgres.properties  # PostgreSQL
└── db/migration/                    # migrations Flyway
```

## Segurança

`SecurityConfig` hoje libera `/h2-console/**` e `/actuator/health/**` e exige
autenticação no resto, com um usuário fixo de desenvolvimento. É um ponto de
partida — substitua por autenticação real antes de expor a aplicação.
