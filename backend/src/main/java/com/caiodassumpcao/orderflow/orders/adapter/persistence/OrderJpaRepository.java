package com.caiodassumpcao.orderflow.orders.adapter.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {}
