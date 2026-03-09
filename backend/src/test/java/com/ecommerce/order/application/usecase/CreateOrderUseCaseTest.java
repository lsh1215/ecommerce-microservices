package com.ecommerce.order.application.usecase;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.infrastructure.application.service.ExchangeRateService;
import com.ecommerce.inventory.application.service.InventoryService;
import com.ecommerce.order.api.dto.request.CreateOrderRequest;
import com.ecommerce.order.api.dto.request.OrderItemRequest;
import com.ecommerce.order.domain.model.Orders;
import com.ecommerce.order.domain.repository.OrderRepository;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductVariant;
import com.ecommerce.product.domain.repository.ProductVariantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private CreateOrderUseCase createOrderUseCase;

    private ProductVariant buildVariant(Long id) {
        Brand brand = Brand.create("Iron Heart", "iron-heart", "JP", "Denim", 2003, null, null);
        Product product = Product.create(brand, "iron-heart-634s", "Denim", "Modern",
                new BigDecimal("300.00"), "USD", null, null, null, null, null, null);
        return ProductVariant.create(product, "IH-634S-M", "M", null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void execute_shouldCreateOrderWithSnapshotData() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L, "123 Main St", "idem-1", "USD",
                List.of(new OrderItemRequest(10L, 2))
        );

        ProductVariant variant = buildVariant(10L);

        given(orderRepository.existsByIdempotencyKey("idem-1")).willReturn(false);
        given(productVariantRepository.findById(10L)).willReturn(Optional.of(variant));
        given(exchangeRateService.convert(any(BigDecimal.class), eq("USD"), eq("USD")))
                .willReturn(new BigDecimal("300.0000"));
        doNothing().when(inventoryService).reserveWithRetry(eq(null), eq(2), any(), any());
        given(orderRepository.save(any(Orders.class))).willAnswer(i -> i.getArgument(0));

        Orders result = createOrderUseCase.execute(request);

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getCustomerId()).isEqualTo(1L);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getProductName()).isEqualTo("iron-heart-634s");
        assertThat(result.getItems().get(0).getBrandName()).isEqualTo("Iron Heart");
        assertThat(result.getStatusHistories()).hasSize(1);
        assertThat(result.getStatusHistories().get(0).getNewStatus()).isEqualTo("PENDING");
        verify(orderRepository).save(any(Orders.class));
    }

    @Test
    void execute_shouldThrowDuplicateOrderOnExistingIdempotencyKey() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L, "addr", "dup-key", "USD",
                List.of(new OrderItemRequest(10L, 1))
        );

        given(orderRepository.existsByIdempotencyKey("dup-key")).willReturn(true);

        assertThatThrownBy(() -> createOrderUseCase.execute(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_ORDER));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void execute_shouldPropagateInsufficientStockException() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L, "addr", "idem-stock", "USD",
                List.of(new OrderItemRequest(10L, 100))
        );

        ProductVariant variant = buildVariant(10L);

        given(orderRepository.existsByIdempotencyKey("idem-stock")).willReturn(false);
        given(productVariantRepository.findById(10L)).willReturn(Optional.of(variant));
        given(exchangeRateService.convert(any(BigDecimal.class), eq("USD"), eq("USD")))
                .willReturn(new BigDecimal("300.0000"));
        doThrow(new BusinessException(ErrorCode.INSUFFICIENT_STOCK))
                .when(inventoryService).reserveWithRetry(any(), eq(100), any(), any());

        assertThatThrownBy(() -> createOrderUseCase.execute(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_STOCK));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void execute_shouldConvertCurrencyForEachItem() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L, "addr", "idem-fx", "KRW",
                List.of(new OrderItemRequest(10L, 1))
        );

        ProductVariant variant = buildVariant(10L);

        given(orderRepository.existsByIdempotencyKey("idem-fx")).willReturn(false);
        given(productVariantRepository.findById(10L)).willReturn(Optional.of(variant));
        given(exchangeRateService.convert(any(BigDecimal.class), eq("USD"), eq("KRW")))
                .willReturn(new BigDecimal("390000.0000"));
        doNothing().when(inventoryService).reserveWithRetry(any(), anyInt(), any(), any());
        given(orderRepository.save(any(Orders.class))).willAnswer(i -> i.getArgument(0));

        Orders result = createOrderUseCase.execute(request);

        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("390000.0000"));
        assertThat(result.getTotalCurrency()).isEqualTo("KRW");
        verify(exchangeRateService).convert(any(BigDecimal.class), eq("USD"), eq("KRW"));
    }
}
