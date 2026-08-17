package com.minhnb.finvera_be;

import com.minhnb.finvera_be.market.repository.MarketIndexRepository;
import com.minhnb.finvera_be.market.repository.MarketIndexSnapshotRepository;
import com.minhnb.finvera_be.market.repository.MarketObservationRepository;
import com.minhnb.finvera_be.market.repository.MarketOverviewRepository;
import com.minhnb.finvera_be.market.repository.MarketBreadthRepository;
import com.minhnb.finvera_be.market.repository.MarketBreadthSnapshotInputRepository;
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
