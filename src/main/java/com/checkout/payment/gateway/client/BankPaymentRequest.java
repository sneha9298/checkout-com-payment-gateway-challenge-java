package com.checkout.payment.gateway.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BankPaymentRequest(
    @JsonProperty("card_number") String cardNumber,
    @JsonProperty("expiry_date") String expiryDate,
    String currency,
    int amount,
    String cvv) {

  // The bank expects a zero-padded MM/YYYY expiry date, e.g. 04/2027.
  public static String formatExpiryDate(int month, int year) {
    return String.format("%02d/%d", month, year);
  }

  @Override
  public String toString() {
    return "BankPaymentRequest{cardNumber='****', expiryDate='" + expiryDate
        + "', currency='" + currency + "', amount=" + amount + ", cvv='***'}";
  }
}
