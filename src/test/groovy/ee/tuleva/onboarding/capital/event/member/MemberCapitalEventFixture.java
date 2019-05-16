package ee.tuleva.onboarding.capital.event.member;

import ee.tuleva.onboarding.user.member.Member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Random;

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent.*;

public class MemberCapitalEventFixture {
    public static MemberCapitalEventBuilder memberCapitalEventFixture(
        Member forMember) {
        return builder()
            .type(MemberCapitalEventType.CAPITAL_PAYMENT)
            .fiatValue(new BigDecimal((new Random()).nextDouble() * 1000))
            .ownershipUnitAmount(new BigDecimal((new Random()).nextDouble() * 1000))
            .member(forMember)
            .accountingDate(LocalDate.now())
            .effectiveDate(LocalDate.now())
            ;
    }
}
