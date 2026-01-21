package ee.tuleva.onboarding.swedbank.fetcher;

import static ee.tuleva.onboarding.banking.BankType.SWEDBANK;
import static ee.tuleva.onboarding.swedbank.Swedbank.SWEDBANK_GATEWAY_TIME_ZONE;

import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayResponseDto;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@RequiredArgsConstructor
@Slf4j
public class SwedbankMessageReceiver {

  private final BankingMessageRepository bankingMessageRepository;
  private final SwedbankGatewayClient swedbankGatewayClient;

  public Optional<BankingMessage> getById(UUID id) {
    return bankingMessageRepository.findById(id);
  }

  // @Scheduled(fixedRateString = "1m")
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
        BankingMessage.builder()
            .bankType(SWEDBANK)
            .requestId(response.requestTrackingId())
            .trackingId(response.responseTrackingId())
            .rawResponse(response.rawResponse())
            .timezone(SWEDBANK_GATEWAY_TIME_ZONE.getId())
            .build();
    bankingMessageRepository.save(messageEntity);

    acknowledgeResponse(response);
  }

  private void acknowledgeResponse(SwedbankGatewayResponseDto response) {
    swedbankGatewayClient.acknowledgeResponse(response);
  }
}
