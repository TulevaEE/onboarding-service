package ee.tuleva.onboarding.mandate.transfer;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.epis.EpisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferExchangeService {

    private final EpisService episService;
    private final FundRepository fundRepository;

    public List<TransferExchange> get(Person person) {
        return episService.getTransferApplications(person).stream()
                .map(transferExchangeDTO -> TransferExchange.builder()
                        .amount(transferExchangeDTO.getAmount())
                        .currency(transferExchangeDTO.getCurrency())
                        .date(transferExchangeDTO.getDate())
                        .currency(transferExchangeDTO.getCurrency())
                        .status(transferExchangeDTO.getStatus())
                        .sourceFund(fundRepository.findByIsin(
                                transferExchangeDTO.getSourceFundIsin()
                        ))
                        .targetFund(fundRepository.findByIsin(
                                transferExchangeDTO.getTargetFundIsin()
                                )
                        )
                        .build()
                )
                .collect(Collectors.toList());
    }

}
