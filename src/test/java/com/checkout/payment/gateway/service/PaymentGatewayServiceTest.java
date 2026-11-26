package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.client.BankPaymentRequest;
import com.checkout.payment.gateway.client.BankPaymentResponse;
import com.checkout.payment.gateway.entity.Payment;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.observability.PaymentMetrics;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.validation.PaymentRequestValidator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentGatewayServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);

  private final PaymentsRepository repository = new PaymentsRepository();
  private final BankClient bankClient = mock(BankClient.class);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final PaymentGatewayService service = new PaymentGatewayService(repository,
      new PaymentRequestValidator(CLOCK), bankClient, new PaymentMetrics(meterRegistry));

  private double processedCount(String status) {
    return meterRegistry.counter("payments.processed", "status", status).count();
  }

  private static PostPaymentRequest validRequest() {
    return new PostPaymentRequest("2222405343248877", 4, 2027, "GBP", 1050, "123");
  }

  @Test
  void authorizedPaymentIsStoredWithLastFourOnly() {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResponse(true, "auth-code"));

    PostPaymentResponse response = service.processPayment(validRequest());

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(response.getCardNumberLastFour()).isEqualTo("8877");
    assertThat(response.getExpiryMonth()).isEqualTo(4);
    assertThat(response.getExpiryYear()).isEqualTo(2027);
    assertThat(response.getCurrency()).isEqualTo("GBP");
    assertThat(response.getAmount()).isEqualTo(1050);

    Payment stored = repository.get(response.getId()).orElseThrow();
    assertThat(stored).usingRecursiveComparison().isEqualTo(new Payment(response.getId(),
        PaymentStatus.AUTHORIZED, "8877", 4, 2027, "GBP", 1050));
    assertThat(processedCount("authorized")).isEqualTo(1);
  }

  @Test
  void declinedPaymentIsStored() {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResponse(false, ""));

    PostPaymentResponse response = service.processPayment(validRequest());

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    assertThat(repository.get(response.getId())).isPresent();
    assertThat(processedCount("declined")).isEqualTo(1);
  }

  @Test
  void bankRequestIsMappedWithZeroPaddedExpiry() {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResponse(true, "auth-code"));

    service.processPayment(validRequest());

    ArgumentCaptor<BankPaymentRequest> captor = ArgumentCaptor.forClass(BankPaymentRequest.class);
    verify(bankClient).authorize(captor.capture());
    assertThat(captor.getValue()).isEqualTo(
        new BankPaymentRequest("2222405343248877", "04/2027", "GBP", 1050, "123"));
  }

  @Test
  void invalidRequestIsRejectedWithoutCallingTheBank() {
    PostPaymentRequest request = validRequest();
    request.setCvv("12");

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(PaymentRejectedException.class)
        .extracting(e -> ((PaymentRejectedException) e).getErrors())
        .asList()
        .containsExactly("cvv must be 3-4 digits");
    verifyNoInteractions(bankClient);
    assertThat(processedCount("rejected")).isEqualTo(1);
  }

  @Test
  void bankUnavailableThrowsAndNothingIsStored() {
    PaymentsRepository spyRepository = spy(new PaymentsRepository());
    PaymentGatewayService serviceWithSpy = new PaymentGatewayService(spyRepository,
        new PaymentRequestValidator(CLOCK), bankClient, new PaymentMetrics(meterRegistry));
    when(bankClient.authorize(any())).thenThrow(new BankUnavailableException("down"));

    assertThatThrownBy(() -> serviceWithSpy.processPayment(validRequest()))
        .isInstanceOf(BankUnavailableException.class);
    verify(spyRepository, never()).add(any());
    assertThat(meterRegistry.find("payments.processed").counters()).isEmpty();
  }

  @Test
  void getPaymentByIdReturnsStoredPayment() {
    Payment payment = new Payment(UUID.randomUUID(), PaymentStatus.AUTHORIZED, "0123", 12, 2027,
        "USD", 10);
    repository.add(payment);

    GetPaymentResponse response = service.getPaymentById(payment.getId());

    assertThat(response).usingRecursiveComparison().isEqualTo(payment);
    assertThat(response.getCardNumberLastFour()).isEqualTo("0123");
  }

  @Test
  void getPaymentByUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.getPaymentById(UUID.randomUUID()))
        .isInstanceOf(PaymentNotFoundException.class);
  }
}
