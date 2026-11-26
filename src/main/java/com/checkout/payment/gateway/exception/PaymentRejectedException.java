package com.checkout.payment.gateway.exception;

import java.util.List;

public class PaymentRejectedException extends RuntimeException {

  private final List<String> errors;

  public PaymentRejectedException(List<String> errors) {
    super("Payment rejected: " + errors);
    this.errors = List.copyOf(errors);
  }

  public List<String> getErrors() {
    return errors;
  }
}
