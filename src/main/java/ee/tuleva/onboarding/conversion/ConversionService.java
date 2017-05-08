package ee.tuleva.onboarding.conversion;

import ee.tuleva.onboarding.account.AccountStatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class ConversionService {

    private final AccountStatementService accountStatementService;

    public ConversionResponse getConversion(Person person) {
        return ConversionResponse.builder().build();
    }

}
