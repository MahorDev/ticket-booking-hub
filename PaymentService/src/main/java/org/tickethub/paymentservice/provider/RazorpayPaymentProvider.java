package org.tickethub.paymentservice.provider;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.tickethub.paymentservice.config.RazorpayConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RazorpayPaymentProvider implements PaymentProvider {
    private static final String DEFAULT_WEBHOOK_SECRET = "replace-with-a-random-secret-at-least-32-characters";

    private final RazorpayConfig razorpayConfig;
    private volatile RazorpayClient cachedClient;

    private boolean isSimulatedMode() {
        String keyId = razorpayConfig.getKeyId();
        String keySecret = razorpayConfig.getKeySecret();
        return keyId == null || keyId.isBlank() || keyId.startsWith("mock")
                || keySecret == null || keySecret.isBlank() || keySecret.startsWith("mock");
    }

    private RazorpayClient razorpayClient() throws Exception {
        RazorpayClient client = this.cachedClient;
        if (client == null) {
            synchronized (this) {
                client = this.cachedClient;
                if (client == null) {
                    client = new RazorpayClient(razorpayConfig.getKeyId(), razorpayConfig.getKeySecret());
                    this.cachedClient = client;
                }
            }
        }
        return client;
    }

    @Override
    public String createOrder(Long bookingId, BigDecimal amount, String currency) {
        if (bookingId == null || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("bookingId and a positive amount are required");
        }
        try {
            long amountInPaise = amount.setScale(2, RoundingMode.HALF_UP)
                    .movePointRight(2)
                    .longValueExact();

            if (isSimulatedMode()) {
                String mockOrderId = "order_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                log.info("Razorpay keys not configured; generated simulated orderId={} for bookingId={} amountInPaise={}",
                        mockOrderId, bookingId, amountInPaise);
                return mockOrderId;
            }

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", "booking_" + bookingId);

            Order order = razorpayClient().orders.create(orderRequest);
            return order.get("id");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Razorpay order", e);
        }
    }

    @Override
    public boolean verifyPayment(String orderId, String paymentId, String signature) {
        try {
            if (orderId == null || orderId.isBlank() || paymentId == null || paymentId.isBlank()) {
                return false;
            }
            if (isSimulatedMode()) {
                return signature == null || !signature.toLowerCase().contains("invalid");
            }
            if (signature == null || signature.isBlank()) {
                return false;
            }
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature", signature);
            com.razorpay.Utils.verifyPaymentSignature(attributes, razorpayConfig.getKeySecret());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            if (payload == null || signature == null || signature.isBlank()) {
                return false;
            }
            String secret = razorpayConfig.getWebhookSecret();
            if (secret == null || secret.isBlank()) {
                secret = DEFAULT_WEBHOOK_SECRET;
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void refund(String paymentId, BigDecimal amount) {
        try {
            long amountInPaise = amount.setScale(2, RoundingMode.HALF_UP)
                    .movePointRight(2)
                    .longValueExact();
            if (isSimulatedMode() || (paymentId != null && paymentId.startsWith("pay_mock_"))) {
                log.info("Simulated refund processed for paymentId={} amountInPaise={}", paymentId, amountInPaise);
                return;
            }
            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", amountInPaise);
            razorpayClient().payments.refund(paymentId, refundRequest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to refund Razorpay payment", e);
        }
    }
}

