package ee.tuleva.onboarding.capital;

import ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CapitalService {

    private final MemberCapitalEventRepository memberCapitalEventRepository;
    private final OrganisationCapitalEventRepository organisationCapitalEventRepository;

    CapitalStatement getCapitalStatement(Long memberId) {
        List<MemberCapitalEvent> events = memberCapitalEventRepository.findAllByMemberId(memberId);

        BigDecimal capitalPaymentAmount =
            events.stream().filter(event -> event.getType() == MemberCapitalEventType.CAPITAL_PAYMENT)
                .map(MemberCapitalEvent::getFiatValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal membershipBonusAmount =
            events.stream().filter(event -> event.getType() == MemberCapitalEventType.MEMBERSHIP_BONUS)
                .map(MemberCapitalEvent::getFiatValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

       return new CapitalStatement(
            membershipBonusAmount, capitalPaymentAmount, BigDecimal.ZERO
       );
    }

    BigDecimal getPricePerOwnershipUnit() {
        return organisationCapitalEventRepository.getTotalValue()
            .divide(memberCapitalEventRepository.getTotalOwnershipUnitAmount(), 7, RoundingMode.HALF_UP);
    }

}
