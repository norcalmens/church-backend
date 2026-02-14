package com.norcalretreat.backend.service;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.norcalretreat.backend.dto.PaymentRequest;
import com.norcalretreat.backend.dto.PaymentResponse;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    public PaymentResponse createPaymentIntent(PaymentRequest request) throws StripeException {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(request.getAmount())
                .setCurrency(request.getCurrency())
                .setDescription(request.getDescription())
                .putMetadata("donor_name", request.getDonorName())
                .putMetadata("donor_email", request.getDonorEmail())
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                .build();

        PaymentIntent paymentIntent = PaymentIntent.create(params);

        return new PaymentResponse(
                paymentIntent.getClientSecret(),
                paymentIntent.getId()
        );
    }

    public PaymentIntent getPaymentIntent(String paymentIntentId) throws StripeException {
        return PaymentIntent.retrieve(paymentIntentId);
    }
}
