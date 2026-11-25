package com.caiodassumpcao.orderflow;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL e Kafka reais (mesmas versões do docker-compose) para testes de integração. */
@TestConfiguration(proxyBeanMethods = false)
@Import({TestcontainersConfiguration.Postgres.class, TestcontainersConfiguration.Kafka.class})
public class TestcontainersConfiguration {

  static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16.16-alpine");
  static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka:4.4.0");

  /** Só o PostgreSQL, para testes que simulam o broker indisponível. */
  @TestConfiguration(proxyBeanMethods = false)
  public static class Postgres {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
      return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
  }

  /** Broker Kafka em modo KRaft. */
  @TestConfiguration(proxyBeanMethods = false)
  public static class Kafka {

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
      return new KafkaContainer(KAFKA_IMAGE);
    }
  }
}
