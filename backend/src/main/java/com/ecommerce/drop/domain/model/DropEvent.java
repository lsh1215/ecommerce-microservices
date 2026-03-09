package com.ecommerce.drop.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ErrorCode;
import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "drop_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DropEvent extends BaseEntity {

    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "ANNOUNCED", Set.of("OPEN", "CLOSED"),
            "OPEN", Set.of("SELLING"),
            "SELLING", Set.of("SOLD_OUT", "CLOSED"),
            "SOLD_OUT", Set.of("CLOSED")
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", unique = true, nullable = false, length = 26, columnDefinition = "char(26)")
    private String publicId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = UlidCreator.getUlid().toString();
        }
    }

    public static DropEvent create(String title, String description,
                                   LocalDateTime startsAt, LocalDateTime endsAt) {
        DropEvent event = new DropEvent();
        event.title = title;
        event.description = description;
        event.status = "ANNOUNCED";
        event.startsAt = startsAt;
        event.endsAt = endsAt;
        return event;
    }

    public void transitionTo(String newStatus) {
        Set<String> allowed = VALID_TRANSITIONS.getOrDefault(this.status, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot transition from " + this.status + " to " + newStatus);
        }
        this.status = newStatus;
    }

    public void update(String title, String description,
                       LocalDateTime startsAt, LocalDateTime endsAt) {
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }
}
