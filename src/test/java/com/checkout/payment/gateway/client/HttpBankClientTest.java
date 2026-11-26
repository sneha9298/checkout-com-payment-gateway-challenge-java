package com.checkout.payment.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.checkout.payment.gateway.exception.BankUnavailableException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class HttpBankClientTest {

  private static final String URL = "http://bank/payments";

  private final RestTemplate restTemplate = new RestTemplate();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
  private final HttpBankClient client =
      new HttpBankClient(restTemplate, "http://bank");

  private final BankPaymentRequest request =
      new BankPaymentRequest("2222405343248877", "04/2027", "GBP", 100, "123");

  @Test
  void sendsSnakeCaseJsonAndMapsAuthorizedResponse() {
    server.expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""
            {"card_number": "2222405343248877", "expiry_date": "04/2027",
             "currency": "GBP", "amount": 100, "cvv": "123"}
            """, true))
        .andRespond(withSuccess("""
            {"authorized": true, "authorization_code": "0bb07405-6d44-4b50-a14f-7ae0beff13ad"}
            """, MediaType.APPLICATION_JSON));

    BankPaymentResponse response = client.authorize(request);

    assertThat(response.authorized()).isTrue();
    assertThat(response.authorizationCode()).isEqualTo("0bb07405-6d44-4b50-a14f-7ae0beff13ad");
    server.verify();
  }

  @Test
  void mapsDeclinedResponse() {
    server.expect(requestTo(URL))
        .andRespond(withSuccess("""
            {"authorized": false, "authorization_code": ""}
            """, MediaType.APPLICATION_JSON));

    assertThat(client.authorize(request).authorized()).isFalse();
  }

  @Test
  void serviceUnavailableThrowsBankUnavailable() {
    server.expect(requestTo(URL)).andRespond(withServiceUnavailable());

    assertThatThrownBy(() -> client.authorize(request))
        .isInstanceOf(BankUnavailableException.class);
  }

  @Test
  void timeoutThrowsBankUnavailable() {
    server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("timeout")));

    assertThatThrownBy(() -> client.authorize(request))
        .isInstanceOf(BankUnavailableException.class);
  }

  @Test
  void connectionRefusedThrowsBankUnavailable() {
    server.expect(requestTo(URL)).andRespond(withException(new ConnectException("refused")));

    assertThatThrownBy(() -> client.authorize(request))
        .isInstanceOf(BankUnavailableException.class);
  }

  @Test
  void badRequestThrowsBankUnavailable() {
    server.expect(requestTo(URL)).andRespond(withBadRequest());

    assertThatThrownBy(() -> client.authorize(request))
        .isInstanceOf(BankUnavailableException.class);
  }

  @Test
  void toStringDoesNotLeakCardNumberOrCvv() {
    assertThat(request.toString())
        .doesNotContain("2222405343248877")
        .doesNotContain("cvv='123'");
  }
}
