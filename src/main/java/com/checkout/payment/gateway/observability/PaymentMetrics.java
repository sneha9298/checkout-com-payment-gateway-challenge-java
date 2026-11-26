package com.checkout.payment.gateway.observability;

import com.checkout.payment.gateway.enums.PaymentStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Counts payment outcomes, tagged by status (authorized / declined / rejected).
 */
@Component
public class PaymentMetrics {

  private final MeterRegistry registry;

  public PaymentMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void paymentProcessed(PaymentStatus status) {
    registry.counter("payments.processed", "status", status.getName().toLowerCase(Locale.ROOT))
        .increment();
  }
}
