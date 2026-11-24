package com.caiodassumpcao.orderflow;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL real (mesma versão do docker-compose) para testes de integração. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16.16-alpine");

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(POSTGRES_IMAGE);
  }
}
