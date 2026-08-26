package com.activeage.payment.service;

import com.activeage.payment.model.PaymentIntent;
import com.activeage.payment.model.PaymentResult;

public interface PaymentService {
    PaymentResult createPayment(PaymentIntent intent);
}