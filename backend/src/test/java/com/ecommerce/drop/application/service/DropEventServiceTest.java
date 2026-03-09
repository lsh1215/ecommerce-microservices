package com.ecommerce.drop.application.service;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.common.exception.ErrorCode;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DropEventServiceTest {

    @Mock
    private DropEventRepository dropEventRepository;

    @Mock
    private DropProductRepository dropProductRepository;

    @Mock
    private DropStatusHistoryRepository dropStatusHistoryRepository;

    @Mock
    private DropAllocationValidator dropAllocationValidator;

    @InjectMocks
    private DropEventService dropEventService;

    private static final LocalDateTime STARTS = LocalDateTime.of(2026, 4, 1, 10, 0);
    private static final LocalDateTime ENDS = LocalDateTime.of(2026, 4, 1, 22, 0);

    private DropEvent buildDropEvent(String title) {
        return DropEvent.create(title, "Description", STARTS, ENDS);
    }

    @Test
    void createDropEvent_shouldSaveEventAndHistory() {
        CreateDropEventRequest request = new CreateDropEventRequest(
                "Spring Drop", "Desc", STARTS, ENDS);
        given(dropEventRepository.save(any(DropEvent.class))).willAnswer(i -> i.getArgument(0));
        given(dropStatusHistoryRepository.save(any(DropStatusHistory.class))).willAnswer(i -> i.getArgument(0));

        DropEventResponse response = dropEventService.createDropEvent(request);

        assertThat(response.title()).isEqualTo("Spring Drop");
        assertThat(response.status()).isEqualTo("ANNOUNCED");

        ArgumentCaptor<DropStatusHistory> historyCaptor = ArgumentCaptor.forClass(DropStatusHistory.class);
        verify(dropStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getPreviousStatus()).isNull();
        assertThat(historyCaptor.getValue().getNewStatus()).isEqualTo("ANNOUNCED");
    }

    @Test
    void getDropEvent_shouldReturnResponseWhenFound() {
        DropEvent event = buildDropEvent("Found Drop");
        given(dropEventRepository.findByPublicId("abc123")).willReturn(Optional.of(event));

        DropEventResponse response = dropEventService.getDropEvent("abc123");

        assertThat(response.title()).isEqualTo("Found Drop");
    }

    @Test
    void getDropEvent_shouldThrowWhenNotFound() {
        given(dropEventRepository.findByPublicId("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> dropEventService.getDropEvent("missing"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listDropEvents_shouldReturnPageResponse() {
        DropEvent event = buildDropEvent("Paginated Drop");
        Page<DropEvent> page = new PageImpl<>(List.of(event), PageRequest.of(0, 10), 1);
        given(dropEventRepository.findAll(any(Pageable.class))).willReturn(page);

        PageResponse<DropEventResponse> response = dropEventService.listDropEvents(PageRequest.of(0, 10));

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void transitionStatus_shouldUpdateStatusAndSaveHistory() {
        DropEvent event = buildDropEvent("Transition Drop");
        given(dropEventRepository.findByPublicId("drop-1")).willReturn(Optional.of(event));
        given(dropEventRepository.save(any(DropEvent.class))).willAnswer(i -> i.getArgument(0));
        given(dropStatusHistoryRepository.save(any(DropStatusHistory.class))).willAnswer(i -> i.getArgument(0));

        DropEventResponse response = dropEventService.transitionStatus("drop-1", "OPEN");

        assertThat(response.status()).isEqualTo("OPEN");

        ArgumentCaptor<DropStatusHistory> historyCaptor = ArgumentCaptor.forClass(DropStatusHistory.class);
        verify(dropStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getPreviousStatus()).isEqualTo("ANNOUNCED");
        assertThat(historyCaptor.getValue().getNewStatus()).isEqualTo("OPEN");
    }

    @Test
    void transitionStatus_shouldThrowOnInvalidTransition() {
        DropEvent event = buildDropEvent("Invalid Transition");
        given(dropEventRepository.findByPublicId("drop-2")).willReturn(Optional.of(event));

        assertThatThrownBy(() -> dropEventService.transitionStatus("drop-2", "SELLING"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }

    @Test
    void addDropProduct_shouldValidateAndSaveProduct() {
        DropEvent event = buildDropEvent("Add Product Drop");
        given(dropEventRepository.findByPublicId("drop-3")).willReturn(Optional.of(event));
        doNothing().when(dropAllocationValidator).validate(1L, 10);
        given(dropProductRepository.save(any(DropProduct.class))).willAnswer(i -> i.getArgument(0));

        AddDropProductRequest request = new AddDropProductRequest(
                1L, 10, new BigDecimal("199.99"), "USD");

        DropProductResponse response = dropEventService.addDropProduct("drop-3", request);

        assertThat(response.productVariantId()).isEqualTo(1L);
        assertThat(response.allocatedQuantity()).isEqualTo(10);
        assertThat(response.dropPriceAmount()).isEqualByComparingTo("199.99");
        verify(dropAllocationValidator).validate(1L, 10);
    }

    @Test
    void addDropProduct_shouldThrowWhenAllocationExceeded() {
        DropEvent event = buildDropEvent("Exceeded Drop");
        given(dropEventRepository.findByPublicId("drop-4")).willReturn(Optional.of(event));
        doThrow(new BusinessException(ErrorCode.DROP_ALLOCATION_EXCEEDED))
                .when(dropAllocationValidator).validate(1L, 1000);

        AddDropProductRequest request = new AddDropProductRequest(
                1L, 1000, new BigDecimal("99.99"), "USD");

        assertThatThrownBy(() -> dropEventService.addDropProduct("drop-4", request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DROP_ALLOCATION_EXCEEDED));
    }

    @Test
    void removeDropProduct_shouldDeleteProduct() {
        DropEvent event = buildDropEvent("Remove Product Drop");
        DropProduct product = DropProduct.create(event, 1L, 10,
                new BigDecimal("100.00"), "USD");
        given(dropProductRepository.findByPublicId("prod-1")).willReturn(Optional.of(product));

        dropEventService.removeDropProduct("prod-1");

        verify(dropProductRepository).delete(product);
    }

    @Test
    void removeDropProduct_shouldThrowWhenNotFound() {
        given(dropProductRepository.findByPublicId("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> dropEventService.removeDropProduct("missing"))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
