package ee.tuleva.onboarding.banking.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentStatusReportHandlerTest {

  @Mock private OutgoingPaymentService outgoingPaymentService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private PaymentStatusReportHandler handler() {
    return new PaymentStatusReportHandler(
        new PaymentStatusReportExtractor(), outgoingPaymentService, eventPublisher);
  }

  @Test
  void aRejectedPaymentIsMarkedFailedAndAlerted() {
    handler()
        .handle(
            report(
                """
                <OrgnlPmtInfAndSts>
                  <TxInfAndSts>
                    <OrgnlEndToEndId>abc123</OrgnlEndToEndId>
                    <TxSts>RJCT</TxSts>
                    <StsRsnInf><Rsn><Cd>AC01</Cd></Rsn></StsRsnInf>
                  </TxInfAndSts>
                </OrgnlPmtInfAndSts>
                """));

    verify(outgoingPaymentService).recordFailed(eq("abc123"), any());
    verify(eventPublisher).publishEvent(new PaymentRejectedEvent("abc123", "AC01"));
  }

  @Test
  void anAcceptedPaymentIsNotTouched() {
    handler()
        .handle(
            report(
                """
                <OrgnlPmtInfAndSts>
                  <TxInfAndSts>
                    <OrgnlEndToEndId>abc123</OrgnlEndToEndId><TxSts>ACSC</TxSts>
                  </TxInfAndSts>
                </OrgnlPmtInfAndSts>
                """));

    verify(outgoingPaymentService, never()).recordFailed(any(), any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  void aFileLevelOnlyReportChangesNothing() {
    handler().handle(report("<GrpSts>ACTC</GrpSts>"));

    verifyNoInteractions(outgoingPaymentService);
    verifyNoInteractions(eventPublisher);
  }

  private static String report(String body) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.002.001.10">
          <CstmrPmtStsRpt>
            %s
          </CstmrPmtStsRpt>
        </Document>
        """
        .formatted(body);
  }
}
