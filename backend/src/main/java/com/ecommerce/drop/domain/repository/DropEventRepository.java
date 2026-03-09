package com.ecommerce.drop.domain.repository;

import com.ecommerce.drop.domain.model.DropEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DropEventRepository extends JpaRepository<DropEvent, Long> {

    Optional<DropEvent> findByPublicId(String publicId);
}
