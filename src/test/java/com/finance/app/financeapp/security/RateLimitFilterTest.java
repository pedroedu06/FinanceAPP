package com.finance.app.financeapp.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitFilterTest {

	private static final Duration WINDOW = Duration.ofMinutes(1);

	private final MutableClock clock = new MutableClock();
	private final RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(10, 10), clock);

	@Test
	void allowsUpToTheLimitAndReopensWhenTheWindowEnds() {
		for (int i = 0; i < 3; i++) {
			assertThat(filter.acquire("login", "10.0.0.1", 3, WINDOW)).isZero();
		}
		assertThat(filter.acquire("login", "10.0.0.1", 3, WINDOW)).isEqualTo(60);

		clock.advance(Duration.ofSeconds(59));
		assertThat(filter.acquire("login", "10.0.0.1", 3, WINDOW)).isEqualTo(1);

		clock.advance(Duration.ofSeconds(1));
		assertThat(filter.acquire("login", "10.0.0.1", 3, WINDOW)).isZero();
	}

	@Test
	void expiredWindowsAreRemovedSoMemoryDoesNotGrow() {
		for (int i = 0; i < 500; i++) {
			filter.acquire("login", "10.0." + (i / 250) + "." + (i % 250), 3, WINDOW);
		}
		assertThat(filter.trackedKeys()).isEqualTo(500);

		clock.advance(WINDOW.plusSeconds(1));
		filter.acquire("login", "10.9.9.9", 3, WINDOW);

		assertThat(filter.trackedKeys()).isEqualTo(1);
	}

	@Test
	void trackedClientsAreCappedAndTheExcessSharesOneCounter() {
		for (int i = 0; i < RateLimitFilter.MAX_TRACKED_KEYS + 500; i++) {
			filter.acquire("login", "cliente-" + i, 3, WINDOW);
		}

		// Mapa cheio: os 500 clientes excedentes cairam no contador comum, que ja estourou.
		assertThat(filter.trackedKeys()).isEqualTo(RateLimitFilter.MAX_TRACKED_KEYS + 1);
		assertThat(filter.acquire("login", "cliente-novo", 3, WINDOW)).isPositive();
		// Quem ja era acompanhado continua com o proprio limite.
		assertThat(filter.acquire("login", "cliente-0", 3, WINDOW)).isZero();
	}

	@Test
	void ipv6AddressesOfTheSameSlash64CountAsOneClient() {
		assertThat(RateLimitFilter.clientKey("2001:db8:aaaa:bbbb::1"))
			.isEqualTo(RateLimitFilter.clientKey("2001:db8:aaaa:bbbb:ffff:ffff:ffff:ffff"))
			.isNotEqualTo(RateLimitFilter.clientKey("2001:db8:aaaa:cccc::1"));
		assertThat(RateLimitFilter.clientKey("203.0.113.7")).isEqualTo("203.0.113.7");
		assertThat(RateLimitFilter.clientKey("0:0:0:0:0:0:0:1")).isEqualTo(RateLimitFilter.clientKey("::1"));
	}

}
