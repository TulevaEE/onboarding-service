package ee.tuleva.onboarding.mandate.batch;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateFileService;
import ee.tuleva.onboarding.mandate.signature.SignatureFile;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MandateBatchService {
  private final MandateBatchRepository mandateBatchRepository;
  private final MandateFileService mandateFileService;

  public Optional<MandateBatch> getByIdAndUser(Long id, User user) {
    var batch =
        mandateBatchRepository
            .findById(id)
            .filter(
                mandateBatch ->
                    mandateBatch.getMandates().stream()
                        .allMatch(mandate -> mandate.getUser().equals(user)));

    if (batch.isEmpty()) {
      log.error("Invalid user {} for mandate batch {}", user.getId(), id);
    }

    return batch;
  }

  public MandateBatch createMandateBatch(List<Mandate> mandates) {
    var mandateBatch =
        MandateBatch.builder().status(MandateBatchStatus.INITIALIZED).mandates(mandates).build();

    return mandateBatchRepository.save(mandateBatch);
  }

  public List<SignatureFile> getMandateBatchContentFiles(Long mandateBatchId, User user) {
    var mandateBatch = getByIdAndUser(mandateBatchId, user).orElseThrow();

    return mandateBatch.getMandates().stream()
        .map(mandateFileService::getMandateFiles)
        .flatMap(List::stream)
        .collect(toList());
  }
}
