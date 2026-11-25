package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;

import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.kyb.CompanySanctionCheck;
import ee.tuleva.onboarding.kyb.RegistryCodeValidator;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.personalcode.PersonalCodeValidator;
import ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Screening;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class PaymentPartyScreener {

  private static final PersonalCodeValidator personalCodeValidator = new PersonalCodeValidator();
  private static final RegistryCodeValidator registryCodeValidator = new RegistryCodeValidator();

  private final SanctionAndPepScreener sanctionAndPepScreener;
  private final CompanySanctionCheck companySanctionCheck;
  private final UserRepository userRepository;

  Screening screen(PartyId party, @Nullable String name) {
    return switch (party.type()) {
      case PERSON ->
          userRepository
              .findByPersonalCode(party.code())
              .map(user -> Screening.of(sanctionAndPepScreener.screeningOutcome(user)))
              .orElseGet(() -> screenByName(party.code(), name));
      case LEGAL_ENTITY -> Screening.of(companySanctionCheck.screeningOutcome(party.code(), name));
    };
  }

  Screening screenPayer(@Nullable String idCode, String name) {
    return partyOf(idCode).map(party -> screen(party, name)).orElseGet(() -> screenByName(name));
  }

  private static Optional<PartyId> partyOf(@Nullable String idCode) {
    if (idCode == null) {
      return Optional.empty();
    }
    if (personalCodeValidator.isValid(idCode)) {
      return Optional.of(new PartyId(PERSON, idCode));
    }
    if (registryCodeValidator.isValid(idCode)) {
      return Optional.of(new PartyId(LEGAL_ENTITY, idCode));
    }
    return Optional.empty();
  }

  Screening screenByName(String name) {
    return Screening.of(sanctionAndPepScreener.nameScreeningOutcome(name));
  }

  private Screening screenByName(String personalCode, @Nullable String nameOnThePayment) {
    return person(personalCode, nameOnThePayment)
        .map(
            person ->
                Screening.of(
                    sanctionAndPepScreener.screeningOutcome(
                        person, sanctionAndPepScreener.recordedCitizenships(person))))
        .orElseGet(
            () -> {
              log.warn("Person without a first and last name cannot be screened");
              return Screening.UNAVAILABLE;
            });
  }

  private static Optional<PersonImpl> person(
      String personalCode, @Nullable String nameOnThePayment) {
    if (nameOnThePayment == null) {
      return Optional.empty();
    }
    var words = nameOnThePayment.strip().split("[\\s,]+");
    if (words.length < 2) {
      return Optional.empty();
    }
    var firstName = String.join(" ", Arrays.copyOf(words, words.length - 1));
    return Optional.of(new PersonImpl(personalCode, firstName, words[words.length - 1]));
  }
}
