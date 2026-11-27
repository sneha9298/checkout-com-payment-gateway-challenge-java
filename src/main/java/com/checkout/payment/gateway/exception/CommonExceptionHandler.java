package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("Exception happened", ex);
    return new ResponseEntity<>(ErrorResponse.of("Page not found"),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(PaymentNotFoundException.class)
  public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex) {
    LOG.info(ex.getMessage());
    return new ResponseEntity<>(ErrorResponse.of("Payment not found"), HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(PaymentRejectedException.class)
  public ResponseEntity<ErrorResponse> handleRejected(PaymentRejectedException ex) {
    return new ResponseEntity<>(ErrorResponse.rejected(ex.getErrors()), HttpStatus.BAD_REQUEST);
  }

  // HttpBankClient has already logged the details.
  @ExceptionHandler(BankUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleBankUnavailable(BankUnavailableException ex) {
    return new ResponseEntity<>(
        ErrorResponse.of("The acquiring bank is unavailable. Please try again later."),
        HttpStatus.BAD_GATEWAY);
  }
}
