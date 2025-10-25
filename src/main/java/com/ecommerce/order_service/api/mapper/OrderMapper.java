package com.ecommerce.order_service.api.mapper;

import com.ecommerce.order_service.api.dto.CreateOrderRequest;
import com.ecommerce.order_service.api.dto.OrderItemRequest;
import com.ecommerce.order_service.api.dto.OrderItemResponse;
import com.ecommerce.order_service.api.dto.OrderResponse;
import com.ecommerce.order_service.domain.entity.Order;
import com.ecommerce.order_service.domain.entity.OrderLine;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper for Order entity and DTOs.
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

    /**
     * Map Order entity to OrderResponse DTO.
     */
    @Mapping(target = "items", source = "orderLines")
    OrderResponse toResponse(Order order);

    /**
     * Map OrderLine entity to OrderItemResponse DTO.
     */
    OrderItemResponse toItemResponse(OrderLine orderLine);

    /**
     * Map list of OrderLine entities to list of OrderItemResponse DTOs.
     */
    List<OrderItemResponse> toItemResponseList(List<OrderLine> orderLines);

    /**
     * Map OrderItemRequest DTO to OrderLine entity.
     */
    OrderLine toOrderLine(OrderItemRequest request);

    /**
     * Map list of OrderItemRequest DTOs to list of OrderLine entities.
     */
    List<OrderLine> toOrderLineList(List<OrderItemRequest> requests);
}

