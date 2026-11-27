package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal.CONTACT_CHANGED;
import static ee.tuleva.onboarding.user.ContactChangeSource.SELF_SERVICE;
import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static ee.tuleva.onboarding.user.ContactField.PHONE;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.user.ContactChange;
import ee.tuleva.onboarding.user.ContactField;
import ee.tuleva.onboarding.user.ReplacedContacts;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.contact.ContactChangeRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({ContactChangeCheck.class, ReplacedContacts.class, ContactChangeRepository.class})
class ContactChangeCheckIT {

  private static final Instant REQUESTED_AT = Instant.parse("2026-11-20T10:00:00Z");
  private static final String APPLICANT_CODE = "38812121215";
  private static final String CHILD_CODE = "61506150006";

  @Autowired ContactChangeCheck contactChangeCheck;
  @Autowired ContactChangeRepository contactChangeRepository;
  @Autowired TestEntityManager entityManager;

  @Test
  void anEmailReplacedSeventyOneHoursBeforeTheRequestIsAContactChange() {
    var applicant = persistedUser(APPLICANT_CODE);
    recordChange(
        applicant, EMAIL, "old@change.example.com", "new@change.example.com", hoursBefore(71));

    assertThat(contactChangeCheck.raisedBy(requestBy(applicant))).contains(CONTACT_CHANGED);
  }

  @Test
  void aPhoneNumberReplacedExactlySeventyTwoHoursBeforeTheRequestIsStillAContactChange() {
    var applicant = persistedUser(APPLICANT_CODE);
    recordChange(applicant, PHONE, "+37255555555", "+37251111111", hoursBefore(72));

    assertThat(contactChangeCheck.raisedBy(requestBy(applicant))).contains(CONTACT_CHANGED);
  }

  @Test
  void aContactReplacedSeventyThreeHoursBeforeTheRequestIsNotAContactChange() {
    var applicant = persistedUser(APPLICANT_CODE);
    recordChange(applicant, PHONE, "+37255555555", "+37251111111", hoursBefore(73));

    assertThat(contactChangeCheck.raisedBy(requestBy(applicant))).isEmpty();
  }

  @Test
  void aContactReplacedAfterTheRequestIsNotAContactChangeBeforeIt() {
    var applicant = persistedUser(APPLICANT_CODE);
    recordChange(
        applicant,
        EMAIL,
        "old@change.example.com",
        "new@change.example.com",
        REQUESTED_AT.plus(1, HOURS));

    assertThat(contactChangeCheck.raisedBy(requestBy(applicant))).isEmpty();
  }

  @Test
  void anEmailWhoseLetterCaseAloneChangedIsNotAContactChange() {
    var applicant = persistedUser(APPLICANT_CODE);
    recordChange(
        applicant, EMAIL, "same@change.example.com", "Same@Change.Example.com", hoursBefore(1));

    assertThat(contactChangeCheck.raisedBy(requestBy(applicant))).isEmpty();
  }

  @Test
  void aFirstEverPhoneNumberIsNotAContactChange() {
    var applicant = persistedUser(APPLICANT_CODE);
    recordChange(applicant, PHONE, null, "+37251111111", hoursBefore(1));

    assertThat(contactChangeCheck.raisedBy(requestBy(applicant))).isEmpty();
  }

  @Test
  void aParentsReplacedContactCountsForTheChildsRedemptionTheParentApplied() {
    var parent = persistedUser(APPLICANT_CODE);
    persistedUser(CHILD_CODE);
    recordChange(parent, PHONE, "+37255555555", "+37251111111", hoursBefore(1));

    var request = requestBy(parent, new PartyId(PERSON, CHILD_CODE));

    assertThat(contactChangeCheck.raisedBy(request)).contains(CONTACT_CHANGED);
  }

  @Test
  void theChildsOwnReplacedContactDoesNotCountForARedemptionTheParentApplied() {
    var parent = persistedUser(APPLICANT_CODE);
    var child = persistedUser(CHILD_CODE);
    recordChange(child, PHONE, "+37255555555", "+37251111111", hoursBefore(1));

    var request = requestBy(parent, new PartyId(PERSON, CHILD_CODE));

    assertThat(contactChangeCheck.raisedBy(request)).isEmpty();
  }

  @Test
  void aBoardMembersReplacedContactCountsForTheCompanysRedemptionTheyApplied() {
    var boardMember = persistedUser(APPLICANT_CODE);
    recordChange(
        boardMember, EMAIL, "old@change.example.com", "new@change.example.com", hoursBefore(1));

    var request = requestBy(boardMember, new PartyId(LEGAL_ENTITY, "10000000"));

    assertThat(contactChangeCheck.raisedBy(request)).contains(CONTACT_CHANGED);
  }

  private static Instant hoursBefore(int hours) {
    return REQUESTED_AT.minus(hours, HOURS);
  }

  private User persistedUser(String personalCode) {
    return entityManager.persistAndFlush(
        User.builder().firstName("Test").lastName("Person").personalCode(personalCode).build());
  }

  private void recordChange(
      User user,
      ContactField field,
      @Nullable String oldValue,
      @Nullable String newValue,
      Instant changedAt) {
    contactChangeRepository.saveAll(
        user.getIdOrThrow(),
        SELF_SERVICE,
        List.of(new ContactChange(field, oldValue, newValue, changedAt)));
  }

  private static RedemptionRequest requestBy(User applicant) {
    return requestBy(applicant, new PartyId(PERSON, applicant.getPersonalCode()));
  }

  private static RedemptionRequest requestBy(User applicant, PartyId party) {
    return redemptionRequestFixture()
        .id(UUID.randomUUID())
        .userId(applicant.getIdOrThrow())
        .partyType(party.type())
        .partyCode(party.code())
        .requestedAt(REQUESTED_AT)
        .build();
  }
}
