package com.ecommerce.drop.domain.repository;

import com.ecommerce.drop.domain.model.DropStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DropStatusHistoryRepository extends JpaRepository<DropStatusHistory, Long> {

    List<DropStatusHistory> findByDropEventIdOrderByCreatedAtDesc(Long dropEventId);
}
