//package org.tickethub.paymentservice.provider;
//
//import org.springframework.context.annotation.Primary;
//import org.springframework.stereotype.Component;
//
//import java.math.BigDecimal;
//import java.util.UUID;
//
//@Component
//@Primary
//public class MockPaymentProvider implements PaymentProvider {
//
//    @Override
//    public String createOrder(Long bookingId, BigDecimal amount, String currency) {
//
//        return "order_test_" + UUID.randomUUID();
//    }
//
//    @Override
//    public boolean verifyPayment(String orderId, String paymentId, String signature) {
//
//        return true;
//    }
//
//    @Override
//    public void refund(String paymentId, BigDecimal amount) {
//
//        System.out.println("Mock refund: " + paymentId + " amount=" + amount);
//    }
//}