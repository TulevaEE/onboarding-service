package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.INITIALIZED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture;
import ee.tuleva.onboarding.mandate.MandateFileService;
import ee.tuleva.onboarding.mandate.generic.GenericMandateService;
import ee.tuleva.onboarding.mandate.signature.SignatureFile;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MandateBatchServiceTest {

  @Mock private MandateBatchRepository mandateBatchRepository;

  @Mock private MandateFileService mandateFileService;
  @Mock private GenericMandateService genericMandateService;

  @InjectMocks private MandateBatchService mandateBatchService;

  @Test
  @DisplayName("return MandateBatch by id and user when all mandates belong to the user")
  void returnMandateBatch() {
    var user = sampleUser().build();
    var mandate1 = sampleFundPensionOpeningMandate();
    var mandate2 = samplePartialWithdrawalMandate();

    var mandateBatch =
        MandateBatchFixture.aMandateBatch().mandates(List.of(mandate1, mandate2)).build();

    when(mandateBatchRepository.findById(any())).thenReturn(Optional.of(mandateBatch));

    Optional<MandateBatch> result = mandateBatchService.getByIdAndUser(1L, user);

    assertThat(result.isPresent()).isTrue();
  }

  @Test
  @DisplayName("return empty when all mandates do not match the user")
  void mandatesDontMatch() {
    var user = sampleUser().build();
    var differentUser = sampleUser().id(2L).build();

    var mandate1 = sampleFundPensionOpeningMandate();
    var mandate2 = samplePartialWithdrawalMandate();
    mandate2.setUser(differentUser);

    var mandateBatch =
        MandateBatchFixture.aMandateBatch().mandates(List.of(mandate1, mandate2)).build();

    when(mandateBatchRepository.findById(any())).thenReturn(Optional.of(mandateBatch));

    Optional<MandateBatch> result = mandateBatchService.getByIdAndUser(1L, user);

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("return mandate batch content files for user")
  void getMandateBatchContentFiles() {
    var mandate1 = sampleFundPensionOpeningMandate();
    var mandate2 = samplePartialWithdrawalMandate();
    var user = mandate1.getUser();

    var mandateBatch =
        MandateBatchFixture.aMandateBatch().mandates(List.of(mandate1, mandate2)).build();

    when(mandateBatchRepository.findById(1L)).thenReturn(Optional.of(mandateBatch));

    when(mandateFileService.getMandateFiles(mandate1))
        .thenReturn(List.of(new SignatureFile("file.html", "text/html", new byte[0])));
    when(mandateFileService.getMandateFiles(mandate2))
        .thenReturn(List.of(new SignatureFile("file2.html", "text/html", new byte[0])));

    List<SignatureFile> result = mandateBatchService.getMandateBatchContentFiles(1L, user);

    assertThat(result.size()).isEqualTo(2);
    verify(mandateBatchRepository, times(1)).findById(1L);
    verify(mandateFileService, times(1)).getMandateFiles(mandate1);
    verify(mandateFileService, times(1)).getMandateFiles(mandate2);
  }

  @Test
  @DisplayName("throw exception when MandateBatch not found for user")
  void notFoundMandateBatch() {
    var user = sampleUser().build();

    when(mandateBatchRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(
        NoSuchElementException.class,
        () -> mandateBatchService.getMandateBatchContentFiles(1L, user));
    verify(mandateBatchRepository, times(1)).findById(1L);
  }

  @Test
  @DisplayName("create MandateBatch")
  void createMandateBatch() {
    var authenticatedPerson =
        AuthenticatedPersonFixture.authenticatedPersonFromUser(sampleUser().build()).build();
    var aFundPensionOpeningMandate = sampleFundPensionOpeningMandate();

    var aMandateBatch =
        MandateBatch.builder()
            .mandates(List.of(aFundPensionOpeningMandate, aFundPensionOpeningMandate))
            .status(INITIALIZED)
            .build();
    var aMandateBatchDto = MandateBatchDto.from(aMandateBatch);

    when(genericMandateService.createGenericMandate(any(), any(), any()))
        .thenReturn(aFundPensionOpeningMandate);
    when(mandateBatchRepository.save(
            argThat(mandateBatch -> mandateBatch.getStatus().equals(INITIALIZED))))
        .thenReturn(aMandateBatch);

    MandateBatch result =
        mandateBatchService.createMandateBatch(authenticatedPerson, aMandateBatchDto);

    assertThat(result.getMandates().size()).isEqualTo(2);
    assertThat(result.getStatus()).isEqualTo(INITIALIZED);
  }
}
