package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.List;
import java.util.UUID;
import com.checkout.payment.gateway.validation.PaymentRequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final PaymentRequestValidator validator;

  public PaymentGatewayService(
      PaymentsRepository paymentsRepository,
      PaymentRequestValidator validator
  ) {
    this.paymentsRepository = paymentsRepository;
    this.validator = validator;
  }

  public GetPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentsRepository.get(id)
        .map(GetPaymentResponse::toPostPaymentResponse)
        .orElseThrow(() -> new PaymentNotFoundException(id));
  }

  public UUID processPayment(PostPaymentRequest paymentRequest) {
    List<String> errors = validator.validate(paymentRequest);
    if (!errors.isEmpty()) {
      LOG.info("Payment rejected: {}", errors);
      throw new PaymentRejectedException(errors);
    }
    return UUID.randomUUID();
  }
}
