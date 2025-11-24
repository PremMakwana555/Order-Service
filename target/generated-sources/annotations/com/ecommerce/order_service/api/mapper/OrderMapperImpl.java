package com.ecommerce.order_service.api.mapper;

import com.ecommerce.order_service.api.dto.OrderItemRequest;
import com.ecommerce.order_service.api.dto.OrderItemResponse;
import com.ecommerce.order_service.api.dto.OrderResponse;
import com.ecommerce.order_service.domain.entity.Order;
import com.ecommerce.order_service.domain.entity.OrderLine;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-10-26T15:17:36+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21 (Oracle Corporation)"
)
@Component
public class OrderMapperImpl implements OrderMapper {

    @Override
    public OrderResponse toResponse(Order order) {
        if ( order == null ) {
            return null;
        }

        OrderResponse.OrderResponseBuilder orderResponse = OrderResponse.builder();

        orderResponse.items( toItemResponseList( order.getOrderLines() ) );
        orderResponse.orderId( order.getOrderId() );
        orderResponse.userId( order.getUserId() );
        orderResponse.status( order.getStatus() );
        orderResponse.totalAmount( order.getTotalAmount() );
        orderResponse.paymentId( order.getPaymentId() );
        orderResponse.shippingAddress( order.getShippingAddress() );
        orderResponse.createdAt( order.getCreatedAt() );
        orderResponse.updatedAt( order.getUpdatedAt() );

        return orderResponse.build();
    }

    @Override
    public OrderItemResponse toItemResponse(OrderLine orderLine) {
        if ( orderLine == null ) {
            return null;
        }

        OrderItemResponse.OrderItemResponseBuilder orderItemResponse = OrderItemResponse.builder();

        orderItemResponse.id( orderLine.getId() );
        orderItemResponse.productId( orderLine.getProductId() );
        orderItemResponse.productName( orderLine.getProductName() );
        orderItemResponse.quantity( orderLine.getQuantity() );
        orderItemResponse.unitPrice( orderLine.getUnitPrice() );
        orderItemResponse.totalPrice( orderLine.getTotalPrice() );

        return orderItemResponse.build();
    }

    @Override
    public List<OrderItemResponse> toItemResponseList(List<OrderLine> orderLines) {
        if ( orderLines == null ) {
            return null;
        }

        List<OrderItemResponse> list = new ArrayList<OrderItemResponse>( orderLines.size() );
        for ( OrderLine orderLine : orderLines ) {
            list.add( toItemResponse( orderLine ) );
        }

        return list;
    }

    @Override
    public OrderLine toOrderLine(OrderItemRequest request) {
        if ( request == null ) {
            return null;
        }

        OrderLine.OrderLineBuilder orderLine = OrderLine.builder();

        orderLine.productId( request.getProductId() );
        orderLine.productName( request.getProductName() );
        orderLine.quantity( request.getQuantity() );
        orderLine.unitPrice( request.getUnitPrice() );

        return orderLine.build();
    }

    @Override
    public List<OrderLine> toOrderLineList(List<OrderItemRequest> requests) {
        if ( requests == null ) {
            return null;
        }

        List<OrderLine> list = new ArrayList<OrderLine>( requests.size() );
        for ( OrderItemRequest orderItemRequest : requests ) {
            list.add( toOrderLine( orderItemRequest ) );
        }

        return list;
    }
}
