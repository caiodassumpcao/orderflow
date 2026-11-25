package com.caiodassumpcao.orderflow.inventory.adapter.persistence;

import com.caiodassumpcao.orderflow.inventory.application.port.StockItemRepository;
import com.caiodassumpcao.orderflow.inventory.domain.Sku;
import com.caiodassumpcao.orderflow.inventory.domain.StockItem;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Produtos em {@code command.stock_items}. */
@Repository
class JdbcStockItemRepository implements StockItemRepository {

  private static final String COLUMNS =
      "sku, name, currency, unit_price, available, reserved, version";

  private final JdbcClient jdbc;

  JdbcStockItemRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<StockItem> find(Sku sku) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM command.stock_items WHERE sku = :sku")
        .param("sku", sku.value())
        .query((rs, row) -> map(rs))
        .optional();
  }

  @Override
  @Transactional(readOnly = true)
  public List<StockItem> findPage(Optional<Sku> after, int limit) {
    // Keyset pelo SKU (chave primária): cada página é uma busca no índice, sem OFFSET.
    String sql =
        "SELECT "
            + COLUMNS
            + " FROM command.stock_items"
            + (after.isPresent() ? " WHERE sku > :after" : "")
            + " ORDER BY sku LIMIT :limit";
    JdbcClient.StatementSpec statement = jdbc.sql(sql).param("limit", limit);
    if (after.isPresent()) {
      statement = statement.param("after", after.get().value());
    }
    return statement.query((rs, row) -> map(rs)).list();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean add(StockItem item) {
    return jdbc.sql(
                """
                INSERT INTO command.stock_items
                       (sku, name, currency, unit_price, available, reserved, version)
                VALUES (:sku, :name, :currency, :unitPrice, :available, :reserved, :version)
                ON CONFLICT (sku) DO NOTHING
                """)
            .param("sku", item.sku().value())
            .param("name", item.name())
            .param("currency", item.currency().getCurrencyCode())
            .param("unitPrice", item.unitPrice())
            .param("available", item.available())
            .param("reserved", item.reserved())
            .param("version", item.version())
            .update()
        == 1;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Map<Sku, StockItem> lockAll(Collection<Sku> skus) {
    Map<Sku, StockItem> bySku = new LinkedHashMap<>();
    if (skus.isEmpty()) {
      return bySku;
    }
    // Travas sempre na ordem do SKU: duas reservas concorrentes nunca se esperam em ciclo.
    List<StockItem> items =
        jdbc.sql(
                "SELECT "
                    + COLUMNS
                    + " FROM command.stock_items WHERE sku IN (:skus) ORDER BY sku FOR UPDATE")
            .param("skus", skus.stream().map(Sku::value).toList())
            .query((rs, row) -> map(rs))
            .list();
    items.forEach(item -> bySku.put(item.sku(), item));
    return bySku;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void saveAll(Collection<StockItem> items) {
    for (StockItem item : items) {
      int updated =
          jdbc.sql(
                  """
                  UPDATE command.stock_items
                     SET available = :available, reserved = :reserved, version = version + 1
                   WHERE sku = :sku AND version = :version
                  """)
              .param("available", item.available())
              .param("reserved", item.reserved())
              .param("sku", item.sku().value())
              .param("version", item.version())
              .update();
      if (updated != 1) {
        throw new OptimisticLockingFailureException("Stock item " + item.sku() + " changed");
      }
    }
  }

  private static StockItem map(ResultSet rs) throws SQLException {
    return new StockItem(
        new Sku(rs.getString("sku")),
        rs.getString("name"),
        Currency.getInstance(rs.getString("currency")),
        rs.getBigDecimal("unit_price"),
        rs.getInt("available"),
        rs.getInt("reserved"),
        rs.getLong("version"));
  }
}
