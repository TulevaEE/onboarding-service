package ee.tuleva.onboarding.capital.transfer

import ee.tuleva.onboarding.user.member.Member
import java.math.BigDecimal
import java.time.LocalDateTime

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.CAPITAL_PAYMENT
import static ee.tuleva.onboarding.user.MemberFixture.memberFixture

class CapitalTransferContractFixture {

    static CapitalTransferContract.CapitalTransferContractBuilder sampleCapitalTransferContract() {
        return CapitalTransferContract.builder()
            .id(1L)
            .seller(memberFixture().id(1L).build())
            .buyer(memberFixture().id(2L).build())
            .iban("EE123456789012345678")
            .transferAmounts(
                List.of(
                    new CapitalTransferContract.CapitalTransferAmount(
                        CAPITAL_PAYMENT, new BigDecimal("100.0"), new BigDecimal("10.0"))))
            .state(CapitalTransferContractState.CREATED)
            .originalContent("original content".getBytes())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
    }

    static CapitalTransferContract.CapitalTransferContractBuilder sampleCapitalTransferContractWithSeller(Member seller) {
        return sampleCapitalTransferContract()
            .seller(seller)
    }

    static CapitalTransferContract.CapitalTransferContractBuilder sampleCapitalTransferContractWithBuyer(Member buyer) {
        return sampleCapitalTransferContract()
            .buyer(buyer)
    }

    static CapitalTransferContract.CapitalTransferContractBuilder sampleCapitalTransferContractWithSellerAndBuyer(Member seller, Member buyer) {
        return sampleCapitalTransferContract()
            .seller(seller)
            .buyer(buyer)
    }

    static CapitalTransferContract.CapitalTransferContractBuilder sampleCapitalTransferContractInState(CapitalTransferContractState state) {
        return sampleCapitalTransferContract()
            .state(state)
    }

    static CapitalTransferContract.CapitalTransferContractBuilder sampleCapitalTransferContractSellerSigned() {
        return sampleCapitalTransferContract()
            .state(CapitalTransferContractState.SELLER_SIGNED)
            .digiDocContainer("seller signed container".getBytes())
    }

    static CapitalTransferContract.CapitalTransferContractBuilder sampleCapitalTransferContractBuyerSigned() {
        return sampleCapitalTransferContract()
            .state(CapitalTransferContractState.BUYER_SIGNED)
            .digiDocContainer("buyer signed container".getBytes())
    }
}
