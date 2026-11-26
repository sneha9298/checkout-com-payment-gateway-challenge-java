package com.checkout.payment.gateway.client;

/**
 * Talks to the acquiring bank. An interface so the service can be tested with a mock and the bank
 * implementation can be swapped.
 */
public interface BankClient {

  BankPaymentResponse authorize(BankPaymentRequest request);
}
