package ee.tuleva.onboarding.savings.fund.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.payment.event.SavingsPaymentCancelledEvent;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.PaymentCancellationRefusedException;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentCancellationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SavingsFundPaymentCancellationAdminController.class)
@Import(AdminTokenValidator.class)
@TestPropertySource(properties = {"admin.api-token=valid-token", "admin.ops-token=ops-token"})
@WithMockUser
@RecordApplicationEvents
class SavingsFundPaymentCancellationAdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SavingFundPaymentCancellationService savingFundPaymentCancellationService;

  @Autowired private ApplicationEvents events;

  private static final String CANCEL_REASON = "Unsupported payment provider";

  private static final String CANCEL_BODY =
      """
      {"reason": "Unsupported payment provider"}
      """;

  @Test
  void cancelSavingsFundPayment_withOpsToken_cancelsWithoutPublishingEvent() throws Exception {
    var paymentId = UUID.randomUUID();
    var cancelledAt = Instant.parse("2026-10-12T09:00:00Z");
    var payment =
        SavingFundPayment.builder()
            .id(paymentId)
            .amount(new BigDecimal("100.00"))
            .status(SavingFundPayment.Status.VERIFIED)
            .cancelledAt(cancelledAt)
            .build();
    given(savingFundPaymentCancellationService.cancelPayment(paymentId, CANCEL_REASON))
        .willReturn(payment);

    mockMvc
        .perform(
            post("/admin/savings-fund/payments/{paymentId}/cancel", paymentId)
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(CANCEL_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
        .andExpect(jsonPath("$.status").value("VERIFIED"))
        .andExpect(jsonPath("$.cancelledAt").value(cancelledAt.toString()));

    assertThat(events.stream(SavingsPaymentCancelledEvent.class)).isEmpty();
  }

  @Test
  void cancelSavingsFundPayment_withAdminToken_returnsOk() throws Exception {
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder()
            .id(paymentId)
            .amount(new BigDecimal("100.00"))
            .status(SavingFundPayment.Status.RECEIVED)
            .cancelledAt(Instant.parse("2026-10-12T09:00:00Z"))
            .build();
    given(savingFundPaymentCancellationService.cancelPayment(paymentId, CANCEL_REASON))
        .willReturn(payment);

    mockMvc
        .perform(
            post("/admin/savings-fund/payments/{paymentId}/cancel", paymentId)
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(CANCEL_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RECEIVED"));
  }

  @Test
  void cancelSavingsFundPayment_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/savings-fund/payments/{paymentId}/cancel", UUID.randomUUID())
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType(APPLICATION_JSON)
                .content(CANCEL_BODY))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(savingFundPaymentCancellationService);
  }

  @Test
  void cancelSavingsFundPayment_whenPaymentMissing_returnsNotFound() throws Exception {
    var paymentId = UUID.randomUUID();
    given(savingFundPaymentCancellationService.cancelPayment(paymentId, CANCEL_REASON))
        .willThrow(new NoSuchElementException());

    mockMvc
        .perform(
            post("/admin/savings-fund/payments/{paymentId}/cancel", paymentId)
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(CANCEL_BODY))
        .andExpect(status().isNotFound());
  }

  @Test
  void cancelSavingsFundPayment_whenCancellationRefused_returnsConflict() throws Exception {
    var paymentId = UUID.randomUUID();
    given(savingFundPaymentCancellationService.cancelPayment(paymentId, CANCEL_REASON))
        .willThrow(new PaymentCancellationRefusedException("Payment already cancelled"));

    mockMvc
        .perform(
            post("/admin/savings-fund/payments/{paymentId}/cancel", paymentId)
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(CANCEL_BODY))
        .andExpect(status().isConflict());

    assertThat(events.stream(SavingsPaymentCancelledEvent.class)).isEmpty();
  }

  @Test
  void cancelSavingsFundPayment_whenReasonRejected_returnsBadRequest() throws Exception {
    var paymentId = UUID.randomUUID();
    given(savingFundPaymentCancellationService.cancelPayment(paymentId, CANCEL_REASON))
        .willThrow(new IllegalArgumentException("Cancellation reason too long"));

    mockMvc
        .perform(
            post("/admin/savings-fund/payments/{paymentId}/cancel", paymentId)
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(CANCEL_BODY))
        .andExpect(status().isBadRequest());
  }

  @Test
  void cancelSavingsFundPayment_withoutBody_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/savings-fund/payments/{paymentId}/cancel", UUID.randomUUID())
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(savingFundPaymentCancellationService);
  }

  @Test
  void cancelSavingsFundPayment_withoutReasonInBody_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/savings-fund/payments/{paymentId}/cancel", UUID.randomUUID())
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(savingFundPaymentCancellationService);
  }
}
