package ee.tuleva.onboarding.capital.transfer.content;

import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class CapitalTransferContractContentService {

  private final ITemplateEngine templateEngine;

  public String generateContractHtml(CapitalTransferContract contract) {
    log.info("Generating HTML content for capital transfer contract {}", contract.getId());

    Context context = buildContext(contract);
    return templateEngine.process("capital_transfer_contract", context).trim();
  }

  public byte[] generateContractContent(CapitalTransferContract contract) {
    String html = generateContractHtml(contract);
    return html.getBytes(UTF_8);
  }

  private Context buildContext(CapitalTransferContract contract) {
    var createTime =
        Optional.ofNullable(contract.getCreatedAt())
            .orElse(LocalDateTime.now(ClockHolder.getClock()));

    return CapitalTransferContractContextBuilder.builder()
        .seller(contract.getSeller())
        .buyer(contract.getBuyer())
        .iban(contract.getIban())
        .transferAmounts(contract.getTransferAmounts())
        .contractState(contract.getState())
        .createdAt(createTime)
        .formattedCreatedAt(formatDateTime(createTime))
        .build();
  }

  private String formatDateTime(LocalDateTime dateTime) {
    if (dateTime == null) {
      return "";
    }
    return dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
  }
}
