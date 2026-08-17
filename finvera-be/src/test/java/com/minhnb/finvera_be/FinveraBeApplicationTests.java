package com.minhnb.finvera_be;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.autoconfigure.exclude="
		+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
		+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
		+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration")
class FinveraBeApplicationTests {

	@Test
	void contextLoads() {
	}

}
