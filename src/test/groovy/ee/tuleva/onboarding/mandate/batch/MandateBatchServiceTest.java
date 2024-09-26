package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFundPensionOpeningMandate;
import static ee.tuleva.onboarding.mandate.MandateFixture.samplePartialWithdrawalMandate;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.INITIALIZED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.mandate.MandateFileService;
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

  @InjectMocks private MandateBatchService mandateBatchService;

  @Test
  @DisplayName("Should return MandateBatch by id and user when all mandates belong to the user")
  void getByIdAndUser_ReturnsMandateBatch_WhenMandatesMatchUser() {
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
  @DisplayName("Should return empty when all mandates do not match the user")
  void getByIdAndUser_ReturnsEmpty_WhenMandatesDoNotMatchUser() {
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
  @DisplayName("Should create and save a new MandateBatch")
  void createMandateBatch_CreatesAndSavesMandateBatch() {
    var mandate1 = sampleFundPensionOpeningMandate();
    var mandate2 = samplePartialWithdrawalMandate();

    var savedMandateBatch =
        MandateBatchFixture.aMandateBatch().mandates(List.of(mandate1, mandate2)).build();
    when(mandateBatchRepository.save(any(MandateBatch.class))).thenReturn(savedMandateBatch);

    MandateBatch createdMandateBatch =
        mandateBatchService.createMandateBatch(List.of(mandate1, mandate2));

    verify(mandateBatchRepository, times(1)).save(any(MandateBatch.class));
    assertThat(createdMandateBatch).isNotNull();
    assertThat(createdMandateBatch.getStatus()).isEqualTo(INITIALIZED);
    assertThat(createdMandateBatch.getMandates().size()).isEqualTo(2);
  }

  @Test
  @DisplayName("Should return mandate batch content files for user")
  void getMandateBatchContentFiles_ReturnsContentFiles_ForUser() {
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
  @DisplayName("Should throw exception when MandateBatch not found for user")
  void getMandateBatchContentFiles_ThrowsException_WhenMandateBatchNotFound() {
    var user = sampleUser().build();

    when(mandateBatchRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(
        NoSuchElementException.class,
        () -> mandateBatchService.getMandateBatchContentFiles(1L, user));
    verify(mandateBatchRepository, times(1)).findById(1L);
  }
}
