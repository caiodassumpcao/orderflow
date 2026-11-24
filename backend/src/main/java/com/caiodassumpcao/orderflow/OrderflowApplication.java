package com.caiodassumpcao.orderflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Ponto de entrada da API do OrderFlow. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class OrderflowApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrderflowApplication.class, args);
  }
}
