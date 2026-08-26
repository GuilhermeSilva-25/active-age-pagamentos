package com.activeage.payment.controller;

import com.activeage.payment.model.PaymentIntent;
import com.activeage.payment.model.PaymentResult;
import com.activeage.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create")
    public ResponseEntity<PaymentResult> createPayment(@RequestBody PaymentIntent intent) {
        PaymentResult result = paymentService.createPayment(intent);

        return ResponseEntity.ok(result);
    }
}