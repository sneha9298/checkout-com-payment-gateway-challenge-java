package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestValidator {

  static final Set<String> SUPPORTED_CURRENCIES = Set.of("GBP", "USD", "EUR");

  private final Clock clock;

  public PaymentRequestValidator(Clock clock) {
    this.clock = clock;
  }

  /**
   * Returns a list of validation errors. An empty list means the request is valid.
   */
  public List<String> validate(PostPaymentRequest request) {
    List<String> errors = new ArrayList<>();

    String cardNumber = request.getCardNumber();
    if (cardNumber == null) {
      errors.add("cardNumber is required");
    } else if (!cardNumber.matches("\\d{14,19}")) {
      errors.add("cardNumber must be 14-19 digits");
    }

    Integer month = request.getExpiryMonth();
    Integer year = request.getExpiryYear();
    boolean monthValid = false;
    if (month == null) {
      errors.add("expiryMonth is required");
    } else if (month < 1 || month > 12) {
      errors.add("expiryMonth must be between 1 and 12");
    } else {
      monthValid = true;
    }
    if (year == null) {
      errors.add("expiryYear is required");
    } else if (monthValid && isInThePast(month, year)) {
      errors.add("card expiry date must be in the future");
    }

    String currency = request.getCurrency();
    if (currency == null) {
      errors.add("currency is required");
    } else if (currency.length() != 3) {
      errors.add("currency must be 3 characters");
    } else if (!SUPPORTED_CURRENCIES.contains(currency)) {
      errors.add("currency must be one of GBP, USD, EUR");
    }

    Integer amount = request.getAmount();
    if (amount == null) {
      errors.add("amount is required");
    } else if (amount <= 0) {
      errors.add("amount must be a positive integer");
    }

    String cvv = request.getCvv();
    if (cvv == null) {
      errors.add("cvv is required");
    } else if (!cvv.matches("\\d{3,4}")) {
      errors.add("cvv must be 3-4 digits");
    }

    return errors;
  }

  // A card is valid until the end of its expiry month, so the current month is accepted.
  // Compared as ints rather than building a YearMonth, which throws for very large years.
  private boolean isInThePast(int month, int year) {
    YearMonth now = YearMonth.now(clock);
    return year < now.getYear() || (year == now.getYear() && month < now.getMonthValue());
  }
}
