package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.aml.exception.AmlChecksMissingException;
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.idcard.IdDocumentType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import ee.tuleva.onboarding.epis.contact.event.ContactDetailsUpdatedEvent;
import ee.tuleva.onboarding.kyc.BeforeKycCheckedEvent;
import ee.tuleva.onboarding.mandate.event.BeforeMandateCreatedEvent;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.user.address.Address;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AmlAutoCheckerTest {

  @Mock private AmlService amlService;
  @Mock private UserService userService;
  @Mock private ContactDetailsService contactDetailsService;

  @InjectMocks private AmlAutoChecker amlAutoChecker;

  @Mock private User mockUser;
  @Mock private ContactDetails mockContactDetails;
  @Mock private Address mockAddress;
  @Mock private IdDocumentType mockIdDocumentType;

  private static AuthenticatedPerson createTestPerson(String personalCode) {
    return AuthenticatedPerson.builder()
        .personalCode(personalCode)
        .firstName("TestFirstName")
        .lastName("TestLastName")
        .userId(1L)
        .build();
  }

  @Test
  @DisplayName(
      "beforeLogin: Should check user with residency status when document type is present and user exists")
  void beforeLogin_checksUser_whenDocumentPresentAndUserExists() {
    // given
    BeforeTokenGrantedEvent mockEvent = mock(BeforeTokenGrantedEvent.class);
    String personalCode = "38001010000";
    AuthenticatedPerson testPerson = createTestPerson(personalCode);

    when(mockEvent.getPerson()).thenReturn(testPerson);
    when(userService.findByPersonalCode(testPerson.getPersonalCode()))
        .thenReturn(Optional.of(mockUser));
    when(mockEvent.getIdDocumentType()).thenReturn(mockIdDocumentType);
    when(mockIdDocumentType.isResident()).thenReturn(true);

    // when
    amlAutoChecker.beforeLogin(mockEvent);

    // then
    verify(amlService).checkUserBeforeLogin(mockUser, testPerson, true);
  }

  @Test
  @DisplayName(
      "beforeLogin: Should check user with null residency status when document type is null and user exists")
  void beforeLogin_checksUser_whenDocumentNullAndUserExists() {
    // given
    BeforeTokenGrantedEvent mockEvent = mock(BeforeTokenGrantedEvent.class);
    String personalCode = "38001010001";
    AuthenticatedPerson testPerson = createTestPerson(personalCode);

    when(mockEvent.getPerson()).thenReturn(testPerson);
    when(userService.findByPersonalCode(testPerson.getPersonalCode()))
        .thenReturn(Optional.of(mockUser));
    when(mockEvent.getIdDocumentType()).thenReturn(null);

    // when
    amlAutoChecker.beforeLogin(mockEvent);

    // then
    verify(amlService).checkUserBeforeLogin(mockUser, testPerson, null);
  }

  @Test
  @DisplayName("beforeLogin: Should throw IllegalStateException if user is not found")
  void beforeLogin_throwsException_whenUserNotFound() {
    // given
    BeforeTokenGrantedEvent mockEvent = mock(BeforeTokenGrantedEvent.class);
    String personalCode = "38001010002";
    AuthenticatedPerson testPerson = createTestPerson(personalCode);

    when(mockEvent.getPerson()).thenReturn(testPerson);
    when(userService.findByPersonalCode(testPerson.getPersonalCode())).thenReturn(Optional.empty());

    // when
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> {
              amlAutoChecker.beforeLogin(mockEvent);
            });

    // then
    assertEquals("User not found with code " + personalCode, exception.getMessage());
    verify(amlService, never()).checkUserBeforeLogin(any(), any(), any());
  }

  @Test
  @DisplayName(
      "afterLogin: Should add pension registry name check if user and contact details are found")
  void afterLogin_addsNameCheck_whenUserAndContactDetailsFound() {
    // given
    AfterTokenGrantedEvent mockEvent = mock(AfterTokenGrantedEvent.class);
    String accessToken = "test-access-token";
    String personalCode = "38001010003";
    AuthenticatedPerson testPerson = createTestPerson(personalCode);

    when(mockEvent.getPerson()).thenReturn(testPerson);
    when(mockEvent.getAccessToken()).thenReturn(accessToken);
    when(userService.findByPersonalCode(testPerson.getPersonalCode()))
        .thenReturn(Optional.of(mockUser));
    when(contactDetailsService.getContactDetails(testPerson, accessToken))
        .thenReturn(mockContactDetails);

    // when
    amlAutoChecker.afterLogin(mockEvent);

    // then
    verify(amlService).addPensionRegistryNameCheckIfMissing(mockUser, mockContactDetails);
  }

  @Test
  @DisplayName("afterLogin: Should not add pension registry name check if user is not found")
  void afterLogin_doesNotAddNameCheck_whenUserNotFound() {
    // given
    AfterTokenGrantedEvent mockEvent = mock(AfterTokenGrantedEvent.class);
    String accessToken = "test-access-token";
    String personalCode = "38001010004";
    AuthenticatedPerson testPerson = createTestPerson(personalCode);

    when(mockEvent.getPerson()).thenReturn(testPerson);
    when(mockEvent.getAccessToken()).thenReturn(accessToken);
    when(userService.findByPersonalCode(testPerson.getPersonalCode())).thenReturn(Optional.empty());

    // when
    amlAutoChecker.afterLogin(mockEvent);

    // then
    verify(contactDetailsService, never())
        .getContactDetails(any(AuthenticatedPerson.class), anyString());
    verify(amlService, never()).addPensionRegistryNameCheckIfMissing(any(), any());
  }

  @Test
  @DisplayName("contactDetailsUpdated: Should add contact details check")
  void contactDetailsUpdated_addsCheck() {
    // given
    ContactDetailsUpdatedEvent mockEvent = mock(ContactDetailsUpdatedEvent.class);
    when(mockEvent.getUser()).thenReturn(mockUser);

    // when
    amlAutoChecker.contactDetailsUpdated(mockEvent);

    // then
    verify(amlService).addContactDetailsCheckIfMissing(mockUser);
  }

  @Test
  @DisplayName(
      "beforeMandateCreated: Should add sanction and PEP check when required, and all checks passed")
  void beforeMandateCreated_addsSanctionAndPepCheck_forThirdPillar_whenChecksPass() {
    // given
    BeforeMandateCreatedEvent mockEvent = mock(BeforeMandateCreatedEvent.class);
    var mandate = thirdPillarMandate();
    Integer pillar = mandate.getPillar();

    when(mockEvent.getUser()).thenReturn(mockUser);
    when(mockEvent.getMandate()).thenReturn(mandate);
    when(mockEvent.getAddress()).thenReturn(mockAddress);
    when(amlService.allChecksPassed(mockUser, mandate)).thenReturn(true);
    when(amlService.isMandateAmlCheckRequired(mockUser, mandate)).thenReturn(true);

    // when
    assertDoesNotThrow(() -> amlAutoChecker.beforeMandateCreated(mockEvent));

    // then
    verify(amlService).addSanctionAndPepCheckIfMissing(mockUser, mockAddress);
    verify(amlService).allChecksPassed(mockUser, mandate);
  }

  @Test
  @DisplayName(
      "beforeMandateCreated: Should not add sanction and PEP check when not required when all checks passed")
  void beforeMandateCreated_noSanctionAndPepCheck_forNonThirdPillar_whenChecksPass() {
    // given
    BeforeMandateCreatedEvent mockEvent = mock(BeforeMandateCreatedEvent.class);
    var mandate = sampleMandate();

    when(mockEvent.getUser()).thenReturn(mockUser);
    when(mockEvent.getMandate()).thenReturn(mandate);
    when(amlService.isMandateAmlCheckRequired(mockUser, mandate)).thenReturn(false);
    when(amlService.allChecksPassed(mockUser, mandate)).thenReturn(true);

    // when
    assertDoesNotThrow(() -> amlAutoChecker.beforeMandateCreated(mockEvent));

    // then
    verify(amlService, never()).addSanctionAndPepCheckIfMissing(any(), any());
    verify(amlService).allChecksPassed(mockUser, mandate);
  }

  @Test
  @DisplayName(
      "beforeMandateCreated: Should throw AmlChecksMissingException if not all checks passed)")
  void beforeMandateCreated_throwsException_whenChecksFail_thirdPillar() {
    // given
    BeforeMandateCreatedEvent mockEvent = mock(BeforeMandateCreatedEvent.class);
    var mandate = thirdPillarMandate();

    when(mockEvent.getUser()).thenReturn(mockUser);
    when(mockEvent.getMandate()).thenReturn(mandate);
    when(mockEvent.getAddress()).thenReturn(mockAddress);
    when(amlService.allChecksPassed(mockUser, mandate)).thenReturn(false);
    when(amlService.isMandateAmlCheckRequired(mockUser, mandate)).thenReturn(true);

    // when
    AmlChecksMissingException exception =
        assertThrows(
            AmlChecksMissingException.class,
            () -> {
              amlAutoChecker.beforeMandateCreated(mockEvent);
            });

    // then
    assertNotNull(exception);
    verify(amlService).addSanctionAndPepCheckIfMissing(mockUser, mockAddress);
    verify(amlService).allChecksPassed(mockUser, mandate);
  }

  @Test
  @DisplayName("beforeKycChecked: Should add sanction and PEP check")
  void beforeKycChecked_addsSanctionAndPepCheck() {
    // given
    var person = createTestPerson("38001010005");
    var event = new BeforeKycCheckedEvent(person, mockAddress);

    // when
    amlAutoChecker.beforeKycChecked(event);

    // then
    verify(amlService).addSanctionAndPepCheckIfMissing(person, mockAddress);
  }
}
