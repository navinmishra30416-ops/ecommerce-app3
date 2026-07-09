package com.ecommerce.payment;

import com.ecommerce.order.Order;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around a real payment provider (Stripe, Razorpay, etc).
 * Swap the body of charge() for an actual SDK call — never store raw
 * card details in your own database; only the provider's transaction
 * reference belongs here.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Payment charge(Order order) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider("stub-provider");

        // TODO: replace with a real call, e.g.:
        // PaymentIntent intent = Stripe charge for order.getTotalAmount()
        // payment.setTransactionRef(intent.getId());
        // payment.setStatus(intent.getStatus().equals("succeeded") ? SUCCEEDED : FAILED);

        payment.setTransactionRef("stub-" + order.getId());
        payment.setStatus(PaymentStatus.SUCCEEDED);

        return paymentRepository.save(payment);
    }
}
