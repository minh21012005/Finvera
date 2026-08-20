package com.minhnb.finvera_be;

import com.minhnb.finvera_be.market.repository.MarketCalendarDayRepository;
import com.minhnb.finvera_be.market.repository.MarketIndexRepository;
import com.minhnb.finvera_be.market.repository.MarketIndexSnapshotRepository;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.market.repository.MarketObservationRepository;
import com.minhnb.finvera_be.market.repository.MarketOverviewRepository;
import com.minhnb.finvera_be.market.repository.MarketBreadthRepository;
import com.minhnb.finvera_be.market.repository.MarketBreadthSnapshotInputRepository;
import com.minhnb.finvera_be.market.repository.MarketSessionWindowRepository;
import com.minhnb.finvera_be.market.repository.RegimeAssessmentRepository;
import com.minhnb.finvera_be.market.repository.RegimeAssessmentInputRepository;
import com.minhnb.finvera_be.market.repository.RegimeFactorRepository;
import com.minhnb.finvera_be.market.repository.SourceReconciliationAuditRepository;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalReportRepository;
import com.minhnb.finvera_be.stock.repository.SectorReferenceRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorResultRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorValueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.UUID;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.autoconfigure.exclude="
		+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
		+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
		+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration")
class FinveraBeApplicationTests {

	@MockitoBean
	MarketObservationRepository marketObservationRepository;

	@MockitoBean
	MarketIndexSnapshotRepository marketIndexSnapshotRepository;

	@MockitoBean
	MarketIndexRepository marketIndexRepository;

	@MockitoBean
	MarketOverviewRepository marketOverviewRepository;

	@MockitoBean
	MarketBreadthRepository marketBreadthRepository;

	@MockitoBean
	MarketBreadthSnapshotInputRepository marketBreadthSnapshotInputRepository;

	@MockitoBean
	RegimeAssessmentRepository regimeAssessmentRepository;

	@MockitoBean
	RegimeAssessmentInputRepository regimeAssessmentInputRepository;

	@MockitoBean
	RegimeFactorRepository regimeFactorRepository;

	@MockitoBean
	SourceReconciliationAuditRepository sourceReconciliationAuditRepository;

	@MockitoBean
	MarketInstrumentRepository marketInstrumentRepository;

	@MockitoBean
	MarketCalendarDayRepository marketCalendarDayRepository;

	@MockitoBean
	MarketSessionWindowRepository marketSessionWindowRepository;

	@MockitoBean
	EquityDailyBarRepository equityDailyBarRepository;

	@MockitoBean
	FundamentalReportRepository fundamentalReportRepository;

	@MockitoBean
	EquityProfileRepository equityProfileRepository;

	@MockitoBean
	SectorReferenceRepository sectorReferenceRepository;

	@MockitoBean
	TechnicalIndicatorResultRepository technicalIndicatorResultRepository;

	@MockitoBean
	TechnicalIndicatorValueRepository technicalIndicatorValueRepository;

	@MockitoBean
	com.minhnb.finvera_be.stock.repository.FundamentalReportMetricRepository fundamentalReportMetricRepository;

	@MockitoBean
	com.minhnb.finvera_be.stock.repository.FundamentalSummaryRepository fundamentalSummaryRepository;

	@MockitoBean
	com.minhnb.finvera_be.stock.repository.FundamentalSummaryMetricRepository fundamentalSummaryMetricRepository;

	@MockitoBean
	com.minhnb.finvera_be.stock.repository.FundamentalSummaryInputRepository fundamentalSummaryInputRepository;

	@MockitoBean
	com.minhnb.finvera_be.stock.repository.FundamentalMetricCatalogRepository fundamentalMetricCatalogRepository;

	@MockitoBean
	com.minhnb.finvera_be.stock.repository.ValuationAssessmentRepository valuationAssessmentRepository;

	@MockitoBean
	com.minhnb.finvera_be.stock.repository.ValuationMetricRepository valuationMetricRepository;

	@MockitoBean
	com.minhnb.finvera_be.stock.repository.ValuationAssessmentInputRepository valuationAssessmentInputRepository;

	@MockitoBean
	com.minhnb.finvera_be.stock.repository.StrategySignalRepository strategySignalRepository;

	@MockitoBean
	com.minhnb.finvera_be.stock.repository.StrategySignalRiskFactorRepository strategySignalRiskFactorRepository;

	@MockitoBean
	com.minhnb.finvera_be.stock.repository.StrategySignalInputRepository strategySignalInputRepository;

	@MockitoBean
	com.minhnb.finvera_be.analyst.repository.AnalystQueryRepository analystQueryRepository;

	@MockitoBean
	com.minhnb.finvera_be.analyst.repository.AnalystToolCallRepository analystToolCallRepository;

	@MockitoBean
	com.minhnb.finvera_be.portfolio.repository.PortfolioRepository portfolioRepository;

	@MockitoBean
	com.minhnb.finvera_be.portfolio.repository.PortfolioTransactionRepository portfolioTransactionRepository;

	@MockitoBean
	com.minhnb.finvera_be.portfolio.repository.WatchlistRepository watchlistRepository;

	@MockitoBean
	com.minhnb.finvera_be.portfolio.repository.WatchlistItemRepository watchlistItemRepository;

	@MockitoBean
	com.minhnb.finvera_be.research.repository.NewsArticleRepository newsArticleRepository;

	@MockitoBean
	com.minhnb.finvera_be.research.repository.ResearchDocumentRepository researchDocumentRepository;

	@MockitoBean
	com.minhnb.finvera_be.research.repository.ResearchChunkRepository researchChunkRepository;

	@DynamicPropertySource
	static void ownerProperties(DynamicPropertyRegistry registry) {
		registry.add("finvera.security.owner.id", UUID::randomUUID);
		registry.add("finvera.security.owner.username", () -> "owner-" + UUID.randomUUID());
		registry.add("finvera.security.owner.password-hash",
				() -> new BCryptPasswordEncoder(4).encode(UUID.randomUUID().toString()));
	}

	@Test
	void contextLoads() {
	}

}
