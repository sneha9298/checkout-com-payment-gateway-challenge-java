package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.exception.BankUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class HttpBankClient implements BankClient {

  private static final Logger LOG = LoggerFactory.getLogger(HttpBankClient.class);

  private final RestTemplate restTemplate;
  private final String paymentsUrl;

  public HttpBankClient(RestTemplate restTemplate, @Value("${bank.simulator.url}") String baseUrl) {
    this.restTemplate = restTemplate;
    this.paymentsUrl = baseUrl + "/payments";
  }

  @Override
  public BankPaymentResponse authorize(BankPaymentRequest request) {
    long start = System.nanoTime();
    BankPaymentResponse response;
    try {
      response = restTemplate.postForObject(paymentsUrl, request, BankPaymentResponse.class);
    } catch (RestClientException e) {
      // 4xx/5xx responses (e.g. the simulator's 503) and connection errors.
      LOG.warn("Bank call failed: {}", e.getMessage());
      throw new BankUnavailableException("Bank is unavailable", e);
    }

    if (response == null) {
      LOG.warn("Bank returned an empty response");
      throw new BankUnavailableException("Bank returned an empty response");
    }

    LOG.info("Bank responded in {} ms, authorized={}", (System.nanoTime() - start) / 1_000_000,
        response.authorized());

    return response;
  }
}
