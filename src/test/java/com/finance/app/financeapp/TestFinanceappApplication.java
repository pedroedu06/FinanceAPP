package com.finance.app.financeapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Sobe a aplicacao com um PostgreSQL descartavel via Testcontainers, sem
 * precisar do docker-compose. Requer o Docker rodando.
 *
 * <pre>./mvnw spring-boot:test-run</pre>
 *
 * Tambem pode ser importado em testes de integracao com
 * {@code @Import(TestFinanceappApplication.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestFinanceappApplication {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
	}

	public static void main(String[] args) {
		SpringApplication.from(FinanceappApplication::main)
			.with(TestFinanceappApplication.class)
			.run(args);
	}

}
