package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.client.BankPaymentRequest;
import com.checkout.payment.gateway.client.BankPaymentResponse;
import com.checkout.payment.gateway.entity.Payment;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.observability.PaymentMetrics;
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
  private final BankClient bankClient;
  private final PaymentMetrics metrics;

  public PaymentGatewayService(
      PaymentsRepository paymentsRepository,
      PaymentRequestValidator validator,
      BankClient bankClient,
      PaymentMetrics metrics
  ) {
    this.paymentsRepository = paymentsRepository;
    this.validator = validator;
    this.bankClient = bankClient;
    this.metrics = metrics;
  }

  public GetPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to payment with ID {}", id);
    return paymentsRepository.get(id)
        .map(GetPaymentResponse::toPostPaymentResponse)
        .orElseThrow(() -> new PaymentNotFoundException(id));
  }

  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    List<String> errors = validator.validate(paymentRequest);
    if (!errors.isEmpty()) {
      LOG.info("Payment rejected: {}", errors);
      metrics.paymentProcessed(PaymentStatus.REJECTED);
      throw new PaymentRejectedException(errors);
    }

    BankPaymentResponse bankResponse = bankClient.authorize(toBankRequest(paymentRequest));
    PaymentStatus status = bankResponse.authorized()
        ? PaymentStatus.AUTHORIZED
        : PaymentStatus.DECLINED;

    String cardNumber = paymentRequest.getCardNumber();
    Payment payment = new Payment(
        UUID.randomUUID(),
        status,
        cardNumber.substring(cardNumber.length() - 4),
        paymentRequest.getExpiryMonth(),
        paymentRequest.getExpiryYear(),
        paymentRequest.getCurrency(),
        paymentRequest.getAmount());
    paymentsRepository.add(payment);

    metrics.paymentProcessed(status);
    LOG.info("Payment {} processed with status {}", payment.getId(), status.getName());
    return PostPaymentResponse.toPostPaymentResponse(payment);
  }

  private static BankPaymentRequest toBankRequest(PostPaymentRequest request) {
    return new BankPaymentRequest(
        request.getCardNumber(),
        BankPaymentRequest.formatExpiryDate(request.getExpiryMonth(), request.getExpiryYear()),
        request.getCurrency(),
        request.getAmount(),
        request.getCvv());
  }
}
