package ee.tuleva.onboarding.swedbank.fetcher;

import static ee.tuleva.onboarding.swedbank.fetcher.SwedbankStatementFetcher.SwedbankAccount.DEPOSIT_EUR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.swedbank.gateway.iso.request.Document;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.time.TestClockHolder;
import jakarta.xml.bind.JAXBElement;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SwedbankStatementFetcherTest {

  private SwedbankGatewayClient swedbankGatewayClient;

  private SwedbankMessageRepository fetchJobRepository;

  private SwedbankStatementFetcher fetcher;

  private SwedbankAccountConfiguration configuration;

  private static final String testIban = "EE_TEST_IBAN";

  @BeforeEach
  void setup() {
    swedbankGatewayClient = mock(SwedbankGatewayClient.class);
    fetchJobRepository = mock(SwedbankMessageRepository.class);
    configuration = mock(SwedbankAccountConfiguration.class);

    fetcher =
        new SwedbankStatementFetcher(
            TestClockHolder.clock, fetchJobRepository, swedbankGatewayClient, configuration);

    when(configuration.getAccountIban(DEPOSIT_EUR)).thenReturn(Optional.of(testIban));
  }

  @Test
  @DisplayName("request sender sends request")
  void testSendRequest() {
    JAXBElement<Document> mockDocument = mock(JAXBElement.class);

    when(swedbankGatewayClient.getIntraDayReportRequestEntity(any(), any()))
        .thenReturn(mockDocument);

    fetcher.sendRequest(DEPOSIT_EUR);

    verify(swedbankGatewayClient, times(1)).sendStatementRequest(eq(mockDocument), any());
  }
}
