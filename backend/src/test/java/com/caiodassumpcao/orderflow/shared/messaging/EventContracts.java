package com.caiodassumpcao.orderflow.shared.messaging;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contratos dos eventos publicados ({@code docs/events/schemas}, JSON Schema 2020-12) e exemplos de
 * mensagens ({@code docs/events/examples}). Os testes de produtor validam o que os publishers
 * gravam; os de consumidor alimentam os listeners com os exemplos.
 */
public final class EventContracts {

  /** Relativo ao diretório do módulo (backend/), onde o Maven roda os testes. */
  static final Path EVENTS = Path.of("..", "docs", "events");

  public static final List<String> TYPES =
      List.of(
          "OrderCreated",
          "OrderConfirmed",
          "OrderShipped",
          "OrderCancelled",
          "StockReserved",
          "StockRejected");

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final SchemaRegistry REGISTRY =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
  private static final Map<String, Schema> SCHEMAS = new ConcurrentHashMap<>();

  private EventContracts() {}

  /** {@code OrderCreated} → {@code order-created}. */
  public static String fileName(String type) {
    return type.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
  }

  private static Schema schema(String file) {
    return SCHEMAS.computeIfAbsent(
        file, name -> REGISTRY.getSchema(read(EVENTS.resolve("schemas").resolve(name))));
  }

  private static String read(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<String> messages(List<Error> errors, String prefix) {
    return errors.stream().map(error -> prefix + error.toString()).toList();
  }

  /** Violações do payload contra o schema do tipo e da versão. */
  public static List<String> payloadViolations(String type, int version, JsonNode payload) {
    return messages(
        schema(fileName(type) + ".v" + version + ".schema.json").validate(payload), "payload: ");
  }

  /** Violações da mensagem inteira: envelope e, conforme o tipo, o payload. */
  public static List<String> messageViolations(JsonNode message) {
    List<String> violations =
        new ArrayList<>(messages(schema("envelope.v1.schema.json").validate(message), ""));
    if (violations.isEmpty()) {
      violations.addAll(
          payloadViolations(
              message.get("type").asString(),
              message.get("version").asInt(),
              message.get("payload")));
    }
    return violations;
  }

  /** A mensagem como o relay a publica no Kafka a partir de uma linha da outbox. */
  public static JsonNode published(OutboxMessage message) {
    String encoded =
        new EventEnvelopeCodec(JSON)
            .encode(
                new EventEnvelope(
                    message.eventId(),
                    message.eventType(),
                    message.eventVersion(),
                    message.aggregateId(),
                    message.aggregateVersion(),
                    message.occurredAt(),
                    message.correlationId(),
                    JSON.readTree(message.payloadJson())));
    return JSON.readTree(encoded);
  }

  public static JsonNode parse(String json) {
    return JSON.readTree(json);
  }

  /** Exemplo de mensagem do tipo (envelope completo, versão 1). */
  public static String example(String type) {
    return read(EVENTS.resolve("examples").resolve(fileName(type) + ".v1.json"));
  }
}
