package com.ecommerce.order.application.saga;

import com.ecommerce.order.application.dto.ProductSnapshotDto;

record ReservedOrderItem(ProductSnapshotDto snapshot, int quantity) {
}
