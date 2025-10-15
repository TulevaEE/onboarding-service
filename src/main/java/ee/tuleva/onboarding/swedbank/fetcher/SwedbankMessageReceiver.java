package ee.tuleva.onboarding.swedbank.fetcher;

import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayResponseDto;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Slf4j
@Service
@Profile("!staging")
public class SwedbankMessageReceiver {

  private final SwedbankMessageRepository swedbankMessageRepository;
  private final SwedbankGatewayClient swedbankGatewayClient;

  public Optional<SwedbankMessage> getById(UUID id) {
    return swedbankMessageRepository.findById(id);
  }

  // @Scheduled(fixedRateString = "1m")
  @Scheduled(cron = "0 0 9-17 * * MON-FRI", zone = "Europe/Tallinn")
  public void getResponses() {
    try {
      getResponse();
    } catch (Exception e) {
      log.error("Swedbank statement response fetcher failed", e);
    }
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
