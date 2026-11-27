package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The body of every error response, so merchants only need to handle one shape. {@code status} is
 * only set ("Rejected") when the payment itself was rejected.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

  @Schema(description = "Only present when the payment was rejected", example = "Rejected")
  private PaymentStatus status;
  @Schema(example = "Payment rejected")
  private String message;
  @Schema(description = "Details, e.g. each validation error. May be empty.",
      example = "[\"cvv must be 3-4 digits\"]")
  private List<String> errors;

  public ErrorResponse() {
  }

  public ErrorResponse(PaymentStatus status, String message, List<String> errors) {
    this.status = status;
    this.message = message;
    this.errors = errors;
  }

  public static ErrorResponse rejected(List<String> errors) {
    return new ErrorResponse(PaymentStatus.REJECTED, "Payment rejected", errors);
  }

  public static ErrorResponse of(String message) {
    return new ErrorResponse(null, message, List.of());
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public List<String> getErrors() {
    return errors;
  }

  public void setErrors(List<String> errors) {
    this.errors = errors;
  }

  @Override
  public String toString() {
    return "ErrorResponse{" +
        "status=" + status +
        ", message='" + message + '\'' +
        ", errors=" + errors +
        '}';
  }
}
