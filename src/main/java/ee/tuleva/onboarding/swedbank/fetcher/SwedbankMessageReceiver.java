package ee.tuleva.onboarding.swedbank.fetcher;

import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayResponseDto;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Slf4j
@Service
public class SwedbankMessageReceiver {

  private final SwedbankMessageRepository swedbankMessageRepository;
  private final SwedbankGatewayClient swedbankGatewayClient;

  public Optional<SwedbankMessage> getById(UUID id) {
    return swedbankMessageRepository.findById(id);
  }

  @Scheduled(fixedRateString = "1m")
  public void getResponses() {
    getResponse();
  }

  public void getResponse() {
    log.info("Running Swedbank statement response fetcher");

    var optionalResponse = swedbankGatewayClient.getResponse();

    if (optionalResponse.isEmpty()) {
      log.info("No Swedbank message available");
      return;
    }

    var response = optionalResponse.get();

    var messageEntity =
        SwedbankMessage.builder()
            .requestId(response.requestTrackingId())
            .trackingId(response.responseTrackingId())
            .rawResponse(response.rawResponse())
            .build();
    swedbankMessageRepository.save(messageEntity);

    acknowledgeResponse(response);

    // TODO processing
  }

  private void acknowledgeResponse(SwedbankGatewayResponseDto response) {
    swedbankGatewayClient.acknowledgeResponse(response);
  }
}
