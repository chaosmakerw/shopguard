package com.codesprint.lesson0831.order;

public record OrderSummary(
        Long orderId,
        Long buyerId,
        String productName,
        String status,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        java.util.Map<String,Object> afterSales
) {
    public OrderSummary(Long orderId,Long buyerId,String productName,String status){this(orderId,buyerId,productName,status,null);}
}
