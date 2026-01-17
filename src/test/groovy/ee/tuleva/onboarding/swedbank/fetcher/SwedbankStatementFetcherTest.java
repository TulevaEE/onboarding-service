package ee.tuleva.onboarding.swedbank.fetcher;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.iso20022.camt060.Document;
import ee.tuleva.onboarding.banking.statement.StatementRequestMessageGenerator;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import jakarta.xml.bind.JAXBElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SwedbankStatementFetcherTest {

  private SwedbankGatewayClient swedbankGatewayClient;
  private StatementRequestMessageGenerator statementRequestMessageGenerator;
  private SwedbankStatementFetcher fetcher;

  private static final String testIban = "EE_TEST_IBAN";

  @BeforeEach
  void setup() {
    swedbankGatewayClient = mock(SwedbankGatewayClient.class);
    statementRequestMessageGenerator = mock(StatementRequestMessageGenerator.class);
    var configuration = mock(SwedbankAccountConfiguration.class);

    fetcher =
        new SwedbankStatementFetcher(
            swedbankGatewayClient, configuration, statementRequestMessageGenerator);

    when(configuration.getAccountIban(DEPOSIT_EUR)).thenReturn(testIban);
  }

  @Test
  @DisplayName("request sender sends request")
  @SuppressWarnings("unchecked")
  void testSendRequest() {
    JAXBElement<Document> mockDocument = mock(JAXBElement.class);

    when(statementRequestMessageGenerator.generateIntraDayReportRequest(eq(testIban), any(), any()))
        .thenReturn(mockDocument);

    fetcher.sendRequest(DEPOSIT_EUR);

    verify(swedbankGatewayClient, times(1)).sendStatementRequest(eq(mockDocument), any());
  }
}
