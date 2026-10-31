package com.projectbyPranayChavan.JournalApp;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/*
 * This full-context test needs a real MySQL database, so it is disabled by default (it made
 * "mvn package" fail on any machine without MySQL). Enable it in CI together with a MySQL
 * service or Testcontainers.
 */
@Disabled("Needs a running MySQL database")
@SpringBootTest
class JournalAppApplicationTests {

	@Test
	void contextLoads() {
	}

}
