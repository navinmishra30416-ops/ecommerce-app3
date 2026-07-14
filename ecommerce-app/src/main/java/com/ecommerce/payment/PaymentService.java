package com.ecommerce.payment;

import com.ecommerce.order.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.math.BigDecimal;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RazorpayClient razorpayClient;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    public PaymentService(PaymentRepository paymentRepository, RazorpayClient razorpayClient) {
        this.paymentRepository = paymentRepository;
        this.razorpayClient = razorpayClient;
    }

    /**
     * Creates a Razorpay order for the given local order and saves a
     * PENDING Payment record. The frontend uses the returned razorpayOrderId
     * to open the Razorpay Checkout popup. Payment is only confirmed later,
     * via verifySignature(), once the signature is validated.
     */
    public Payment createRazorpayOrder(Order order) {
        try {
            JSONObject orderRequest = new JSONObject();
            // Razorpay expects amount in paise (smallest currency unit)
            int amountInPaise = order.getTotalAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .intValue();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", order.getId().toString());

            com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);

            Payment payment = new Payment();
            payment.setOrder(order);
            payment.setProvider("razorpay");
            payment.setTransactionRef(razorpayOrder.get("id"));
            payment.setStatus(PaymentStatus.PENDING);

            return paymentRepository.save(payment);
        } catch (RazorpayException e) {
            throw new RuntimeException("Failed to create Razorpay order: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies the signature returned by Razorpay Checkout after the user
     * completes payment. Only trust a payment as successful if this returns true.
     */
    public boolean verifySignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", razorpayOrderId);
            options.put("razorpay_payment_id", razorpayPaymentId);
            options.put("razorpay_signature", razorpaySignature);

            return Utils.verifyPaymentSignature(options, keySecret);
        } catch (Exception e) {
            return false;
        }
    }
    /**
     * Called after signature verification succeeds. Updates the Payment
     * record from PENDING to SUCCEEDED, and swaps the stored reference
     * from the Razorpay order id to the actual Razorpay payment id.
     */
    public void markSucceeded(UUID orderId, String razorpayPaymentId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for order: " + orderId));
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setTransactionRef(razorpayPaymentId);
        paymentRepository.save(payment);
    }
}