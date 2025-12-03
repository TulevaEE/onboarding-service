package ee.tuleva.onboarding.swedbank.fetcher;

import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayResponseDto;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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

  @Scheduled(fixedRateString = "1m")
  @SchedulerLock(
      name = "SwedbankMessageReceiver_getResponses",
      lockAtMostFor = "50s",
      lockAtLeastFor = "5s")
  public void getResponses() {
    log.info("Running Swedbank statement response fetcher");

    try {
      Stream.generate(swedbankGatewayClient::getResponse)
          .takeWhile(Optional::isPresent)
          .map(Optional::get)
          .forEach(this::handleResponse);
    } catch (Exception e) {
      log.error("Swedbank statement response fetcher failed", e);
    }
  }

  private void handleResponse(SwedbankGatewayResponseDto response) {
    var messageEntity =
        SwedbankMessage.builder()
            .requestId(response.requestTrackingId())
            .trackingId(response.responseTrackingId())
            .rawResponse(response.rawResponse())
            .build();
    swedbankMessageRepository.save(messageEntity);

    acknowledgeResponse(response);
  }

  private void acknowledgeResponse(SwedbankGatewayResponseDto response) {
    swedbankGatewayClient.acknowledgeResponse(response);
  }
}
