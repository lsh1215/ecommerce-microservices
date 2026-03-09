package com.ecommerce.inventory.domain.repository;

import com.ecommerce.inventory.domain.model.InventoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryEventRepository extends JpaRepository<InventoryEvent, Long> {

    List<InventoryEvent> findByInventoryIdOrderByCreatedAtDesc(Long inventoryId);
}
