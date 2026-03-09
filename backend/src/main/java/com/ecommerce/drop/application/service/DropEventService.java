package com.ecommerce.drop.application.service;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.drop.api.dto.request.AddDropProductRequest;
import com.ecommerce.drop.api.dto.request.CreateDropEventRequest;
import com.ecommerce.drop.api.dto.response.DropEventResponse;
import com.ecommerce.drop.api.dto.response.DropProductResponse;
import com.ecommerce.drop.domain.model.DropEvent;
import com.ecommerce.drop.domain.model.DropProduct;
import com.ecommerce.drop.domain.model.DropStatusHistory;
import com.ecommerce.drop.domain.repository.DropEventRepository;
import com.ecommerce.drop.domain.repository.DropProductRepository;
import com.ecommerce.drop.domain.repository.DropStatusHistoryRepository;
import com.ecommerce.drop.domain.service.DropAllocationValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DropEventService {

    private final DropEventRepository dropEventRepository;
    private final DropProductRepository dropProductRepository;
    private final DropStatusHistoryRepository dropStatusHistoryRepository;
    private final DropAllocationValidator dropAllocationValidator;

    @Transactional
    public DropEventResponse createDropEvent(CreateDropEventRequest request) {
        DropEvent event = DropEvent.create(
                request.title(), request.description(),
                request.startsAt(), request.endsAt());
        DropEvent saved = dropEventRepository.save(event);

        DropStatusHistory history = DropStatusHistory.create(saved, null, "ANNOUNCED");
        dropStatusHistoryRepository.save(history);

        return DropEventResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public DropEventResponse getDropEvent(String publicId) {
        DropEvent event = findByPublicId(publicId);
        return DropEventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public PageResponse<DropEventResponse> listDropEvents(Pageable pageable) {
        Page<DropEvent> page = dropEventRepository.findAll(pageable);
        return PageResponse.from(page, DropEventResponse::from);
    }

    @Transactional
    public DropEventResponse transitionStatus(String publicId, String newStatus) {
        DropEvent event = findByPublicId(publicId);
        String previousStatus = event.getStatus();
        event.transitionTo(newStatus);
        DropEvent saved = dropEventRepository.save(event);

        DropStatusHistory history = DropStatusHistory.create(saved, previousStatus, newStatus);
        dropStatusHistoryRepository.save(history);

        return DropEventResponse.from(saved);
    }

    @Transactional
    public DropProductResponse addDropProduct(String dropPublicId, AddDropProductRequest request) {
        DropEvent event = findByPublicId(dropPublicId);
        dropAllocationValidator.validate(request.productVariantId(), request.allocatedQuantity());

        DropProduct product = DropProduct.create(
                event, request.productVariantId(),
                request.allocatedQuantity(), request.dropPriceAmount(),
                request.dropPriceCurrency());
        DropProduct saved = dropProductRepository.save(product);

        return DropProductResponse.from(saved);
    }

    @Transactional
    public void removeDropProduct(String dropProductPublicId) {
        DropProduct product = dropProductRepository.findByPublicId(dropProductPublicId)
                .orElseThrow(() -> new EntityNotFoundException("DropProduct", dropProductPublicId));
        dropProductRepository.delete(product);
    }

    private DropEvent findByPublicId(String publicId) {
        return dropEventRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("DropEvent", publicId));
    }
}
