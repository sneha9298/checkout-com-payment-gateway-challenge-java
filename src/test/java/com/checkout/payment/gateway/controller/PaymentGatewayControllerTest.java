package com.checkout.payment.gateway.controller;


import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.client.BankPaymentResponse;
import com.checkout.payment.gateway.entity.Payment;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Year;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  private static final int NEXT_YEAR = Year.now().getValue() + 1;

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentsRepository paymentsRepository;
  @MockBean
  BankClient bankClient;

  private static String paymentJson(String cardNumber, String cvv) {
    return """
        {"cardNumber": "%s", "expiryMonth": 4, "expiryYear": %d,
         "currency": "GBP", "amount": 1050, "cvv": "%s"}
        """.formatted(cardNumber, NEXT_YEAR, cvv);
  }

  private MvcResult postPayment(String json) throws Exception {
    return mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json))
        .andReturn();
  }

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    Payment payment = new Payment(UUID.randomUUID(), PaymentStatus.AUTHORIZED, "4321", 12, 2024,
        "USD", 10);
    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payments/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(payment.getId().toString()))
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payments/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Payment not found"))
        .andExpect(jsonPath("$.errors").isEmpty())
        .andExpect(jsonPath("$.status").doesNotExist());
  }

  @Test
  void whenPaymentIdIsNotAUuidThen400IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payments/not-a-uuid"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void authorizedPaymentReturns201AndCanBeRetrieved() throws Exception {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResponse(true, "auth-code"));

    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson("2222405343248877", "123")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8877"))
        .andExpect(jsonPath("$.expiryMonth").value(4))
        .andExpect(jsonPath("$.expiryYear").value(NEXT_YEAR))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(1050))
        .andExpect(jsonPath("$.cardNumber").doesNotExist())
        .andExpect(jsonPath("$.cvv").doesNotExist());

    String body = postPayment(paymentJson("2222405343248877", "123"))
        .getResponse().getContentAsString();
    String id = JsonPath.read(body, "$.id");
    mvc.perform(MockMvcRequestBuilders.get("/payments/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8877"));
  }

  @Test
  void declinedPaymentReturns201WithDeclinedStatus() throws Exception {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResponse(false, ""));

    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson("2222405343248878", "123")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8878"));
  }

  @Test
  void invalidPaymentReturns400RejectedAndBankIsNotCalled() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson("1234", "12")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Payment rejected"))
        .andExpect(jsonPath("$.errors").value(hasItem("cardNumber must be 14-19 digits")))
        .andExpect(jsonPath("$.errors").value(hasItem("cvv must be 3-4 digits")));

    verifyNoInteractions(bankClient);
  }

  @Test
  void emptyBodyReturns400WithAllRequiredFieldErrors() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors.length()").value(6));

    verifyNoInteractions(bankClient);
  }

  @Test
  void bankUnavailableReturns502() throws Exception {
    when(bankClient.authorize(any())).thenThrow(new BankUnavailableException("down"));

    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson("2222405343248870", "123")))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value(
            "The acquiring bank is unavailable. Please try again later."));
  }

  @Test
  void healthEndpointIsUp() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void processedPaymentsAreCountedByStatus() throws Exception {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResponse(true, "auth-code"));
    postPayment(paymentJson("2222405343248877", "123"));

    // The Spring context is shared between tests, so other tests may have added to the count.
    mvc.perform(MockMvcRequestBuilders.get("/actuator/metrics/payments.processed")
            .param("tag", "status:authorized"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.measurements[0].value").value(greaterThanOrEqualTo(1.0)));
  }
}
