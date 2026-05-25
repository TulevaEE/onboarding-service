package ee.tuleva.onboarding.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class InvestorCountGuardrailJobTest {

  @Mock private InvestorStatisticsRepository investorStatisticsRepository;
  @Mock private InvestorCountGuardrail investorCountGuardrail;
  @InjectMocks private InvestorCountGuardrailJob job;

  private Logger logger;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(InvestorCountGuardrailJob.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logAppender);
  }

  @Test
  void logsNoError_whenNoViolations() {
    given(investorStatisticsRepository.getActiveInvestorCount()).willReturn(85224L);
    given(investorStatisticsRepository.getPreviousActiveInvestorCount())
        .willReturn(OptionalLong.of(85000L));
    given(investorCountGuardrail.findViolations(85224L, OptionalLong.of(85000L)))
        .willReturn(List.of());

    job.checkInvestorCount();

    assertThat(logAppender.list).noneMatch(event -> event.getLevel() == Level.ERROR);
  }

  @Test
  void logsError_whenViolations() {
    given(investorStatisticsRepository.getActiveInvestorCount()).willReturn(120000L);
    given(investorStatisticsRepository.getPreviousActiveInvestorCount())
        .willReturn(OptionalLong.of(85000L));
    given(investorCountGuardrail.findViolations(120000L, OptionalLong.of(85000L)))
        .willReturn(List.of("count out of expected bounds: count=120000, expected=[80000, 95000]"));

    job.checkInvestorCount();

    assertThat(logAppender.list).anyMatch(event -> event.getLevel() == Level.ERROR);
  }
}
