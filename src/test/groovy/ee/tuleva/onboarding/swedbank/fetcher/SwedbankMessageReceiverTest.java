package ee.tuleva.onboarding.swedbank.fetcher;

import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayResponseDto;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SwedbankMessageReceiverTest {

  private SwedbankGatewayClient swedbankGatewayClient;

  private SwedbankMessageRepository messageRepository;

  private SwedbankMessageReceiver receiver;

  @BeforeEach
  void setup() {
    swedbankGatewayClient = mock(SwedbankGatewayClient.class);
    messageRepository = mock(SwedbankMessageRepository.class);

    receiver = new SwedbankMessageReceiver(messageRepository, swedbankGatewayClient);
  }

  @Test
  @DisplayName("response getter receives and saves response")
  void getAndSaveResponse() {
    var id = "3e79ad6aa2fd4118a6dc015de60461a8";
    var trackingId = "11111111a2fd4118a6dc015de60461a8";

    var responseBody = "<xml>TEST</xml>";

    var mockSwedbankResponse =
        new SwedbankGatewayResponseDto(responseBody, id, trackingId.toString());
    when(swedbankGatewayClient.getResponse()).thenReturn(Optional.of(mockSwedbankResponse));

    receiver.getResponse();

    verify(messageRepository, times(1))
        .save(
            argThat(
                (message) ->
                    message.getRequestId().equals(id)
                        && message.getTrackingId().equals(trackingId)
                        && message.getRawResponse().equals(responseBody)));
    verify(swedbankGatewayClient, times(1)).acknowledgeResponse(eq(mockSwedbankResponse));
  }

  @Test
  @DisplayName("response getter does nothing when no message")
  void emptyResponse() {

    when(swedbankGatewayClient.getResponse()).thenReturn(Optional.empty());

    receiver.getResponse();

    verify(messageRepository, never()).save(any());
    verify(swedbankGatewayClient, never()).acknowledgeResponse(any());
  }
}
