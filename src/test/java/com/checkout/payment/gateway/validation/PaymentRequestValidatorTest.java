package com.checkout.payment.gateway.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentRequestValidatorTest {

  // "Today" is 15 June 2026 for every test.
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);

  private final PaymentRequestValidator validator = new PaymentRequestValidator(CLOCK);

  private static PostPaymentRequest validRequest() {
    return new PostPaymentRequest("2222405343248877", 4, 2027, "GBP", 100, "123");
  }

  private List<String> validate(PostPaymentRequest request) {
    return validator.validate(request);
  }

  @Test
  void validRequestHasNoErrors() {
    assertThat(validate(validRequest())).isEmpty();
  }

  @Test
  void allFieldsMissingReportsEachOne() {
    assertThat(validate(new PostPaymentRequest())).containsExactlyInAnyOrder(
        "cardNumber is required",
        "expiryMonth is required",
        "expiryYear is required",
        "currency is required",
        "amount is required",
        "cvv is required");
  }

  @ParameterizedTest
  @ValueSource(strings = {"2222405343248", "22224053432488771234", "2222-4053-4324-8877",
      "22224053432488ab", ""})
  void invalidCardNumberIsRejected(String cardNumber) {
    PostPaymentRequest request = validRequest();
    request.setCardNumber(cardNumber);
    assertThat(validate(request)).containsExactly("cardNumber must be 14-19 digits");
  }

  @ParameterizedTest
  @ValueSource(strings = {"22224053432488", "2222405343248877123"})
  void cardNumberAtLengthBoundsIsAccepted(String cardNumber) {
    PostPaymentRequest request = validRequest();
    request.setCardNumber(cardNumber);
    assertThat(validate(request)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 13, -1})
  void invalidExpiryMonthIsRejected(int month) {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(month);
    assertThat(validate(request)).containsExactly("expiryMonth must be between 1 and 12");
  }

  @Test
  void expiryInThePastIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(5);
    request.setExpiryYear(2026);
    assertThat(validate(request)).containsExactly("card expiry date must be in the future");
  }

  @Test
  void expiryInAPastYearIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(12);
    request.setExpiryYear(2025);
    assertThat(validate(request)).containsExactly("card expiry date must be in the future");
  }

  @Test
  void expiryInTheCurrentMonthIsAccepted() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(6);
    request.setExpiryYear(2026);
    assertThat(validate(request)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"GB", "GBPP", ""})
  void currencyWithWrongLengthIsRejected(String currency) {
    PostPaymentRequest request = validRequest();
    request.setCurrency(currency);
    assertThat(validate(request)).containsExactly("currency must be 3 characters");
  }

  @ParameterizedTest
  @ValueSource(strings = {"JPY", "gbp"})
  void unsupportedCurrencyIsRejected(String currency) {
    PostPaymentRequest request = validRequest();
    request.setCurrency(currency);
    assertThat(validate(request)).containsExactly("currency must be one of GBP, USD, EUR");
  }

  @ParameterizedTest
  @ValueSource(strings = {"GBP", "USD", "EUR"})
  void supportedCurrencyIsAccepted(String currency) {
    PostPaymentRequest request = validRequest();
    request.setCurrency(currency);
    assertThat(validate(request)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -100})
  void nonPositiveAmountIsRejected(int amount) {
    PostPaymentRequest request = validRequest();
    request.setAmount(amount);
    assertThat(validate(request)).containsExactly("amount must be a positive integer");
  }

  @ParameterizedTest
  @ValueSource(strings = {"12", "12345", "12a", ""})
  void invalidCvvIsRejected(String cvv) {
    PostPaymentRequest request = validRequest();
    request.setCvv(cvv);
    assertThat(validate(request)).containsExactly("cvv must be 3-4 digits");
  }

  @ParameterizedTest
  @ValueSource(strings = {"012", "1234"})
  void validCvvIsAccepted(String cvv) {
    PostPaymentRequest request = validRequest();
    request.setCvv(cvv);
    assertThat(validate(request)).isEmpty();
  }

  @Test
  void toStringDoesNotLeakCardNumberOrCvv() {
    String text = validRequest().toString();
    assertThat(text).doesNotContain("2222405343248877").doesNotContain("cvv='123'");
  }
}
