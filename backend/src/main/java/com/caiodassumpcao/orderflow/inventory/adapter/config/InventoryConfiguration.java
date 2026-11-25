package com.caiodassumpcao.orderflow.inventory.adapter.config;

import com.caiodassumpcao.orderflow.inventory.application.InventoryQueryService;
import com.caiodassumpcao.orderflow.inventory.application.StockReservationService;
import com.caiodassumpcao.orderflow.inventory.application.port.InventoryEventOutbox;
import com.caiodassumpcao.orderflow.inventory.application.port.InventoryUnitOfWork;
import com.caiodassumpcao.orderflow.inventory.application.port.StockItemRepository;
import com.caiodassumpcao.orderflow.inventory.application.port.StockReservationRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Liga os casos de uso do inventário (Java puro) às implementações das portas. */
@Configuration(proxyBeanMethods = false)
public class InventoryConfiguration {

  @Bean
  StockReservationService stockReservationService(
      StockItemRepository stockItems,
      StockReservationRepository reservations,
      InventoryEventOutbox outbox,
      InventoryUnitOfWork unitOfWork,
      Clock clock) {
    return new StockReservationService(stockItems, reservations, outbox, unitOfWork, clock);
  }

  @Bean
  InventoryQueryService inventoryQueryService(StockItemRepository stockItems) {
    return new InventoryQueryService(stockItems);
  }
}
