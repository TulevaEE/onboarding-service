package ee.tuleva.onboarding.mandate.statistics;

import ee.tuleva.onboarding.mandate.Mandate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundTransferStatisticsService {

    private final FundTransferStatisticsRepository fundTransferStatisticsRepository;

    public void addFrom(Mandate mandate, List<FundValueStatistics> fundValueStatisticsList) {
        mandate.getFundTransferExchanges().forEach(fundTransferExchange -> {

            FundValueStatistics valueStatForCurrentIsin =
                    getFundValueStatisticsByIsin(fundValueStatisticsList, fundTransferExchange.getSourceFundIsin());

            FundTransferStatistics transferStat = getFundTransferStatistics(fundTransferExchange.getSourceFundIsin());

            transferStat.setTransferred(
                    transferStat.getTransferred().add(
                            fundTransferExchange.getAmount().multiply(valueStatForCurrentIsin.getValue())
                    )
            );

            transferStat.setValue(
                    transferStat.getValue().add(valueStatForCurrentIsin.getValue())
            );

            fundTransferStatisticsRepository.save(transferStat);
        });
    }

    private FundValueStatistics getFundValueStatisticsByIsin(List<FundValueStatistics> fundValueStatisticsList, String isin) {
        return fundValueStatisticsList.stream()
                .filter( valueStat -> valueStat.getIsin().equals(isin))
                .findFirst().orElseThrow(() -> new RuntimeException("FundValueStatistics not in sync with Mandate"));
    }

    private FundTransferStatistics getFundTransferStatistics(String isin) {
        FundTransferStatistics transferStat = fundTransferStatisticsRepository
                .findOneByIsin(isin);

        if(transferStat == null) {
            transferStat = FundTransferStatistics.builder()
                    .transferred(BigDecimal.ZERO)
                    .value(BigDecimal.ZERO)
                    .isin(isin)
                    .build();
        }

        return transferStat;
    }

}
