package com.finance.app.financeapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limita por IP as rotas publicas (login e cadastro), que custam um BCrypt por
 * chamada. E uma janela fixa em memoria: protege uma instancia so. Com mais de
 * uma instancia, ou atras de um proxy, o limite principal deve ficar no
 * proxy/gateway (e o IP real precisa chegar via server.forward-headers-strategy).
 *
 * A memoria e limitada: janelas vencidas sao removidas periodicamente e ha um teto
 * de clientes acompanhados. Acima do teto, os clientes novos dividem um unico
 * contador, de modo que uma enxurrada de IPs nao cresce o mapa nem escapa do limite.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

	static final int MAX_TRACKED_KEYS = 10_000;

	private static final Logger audit = LoggerFactory.getLogger("security.audit");
	private static final Duration LOGIN_WINDOW = Duration.ofMinutes(1);
	private static final Duration REGISTER_WINDOW = Duration.ofHours(1);
	private static final long SWEEP_INTERVAL_MILLIS = Duration.ofSeconds(30).toMillis();
	private static final String OVERFLOW_CLIENT = "overflow";

	private record Window(long startMillis, long lengthMillis, int count) {

		boolean expired(long now) {
			return now - startMillis >= lengthMillis;
		}

	}

	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
	private final AtomicLong nextSweepMillis = new AtomicLong();
	private final RateLimitProperties properties;
	private final Clock clock;

	public RateLimitFilter(RateLimitProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		boolean post = "POST".equals(request.getMethod());

		long retryAfterSeconds = 0;
		if (post && "/auth/login".equals(path)) {
			retryAfterSeconds = acquire("login", clientKey(request.getRemoteAddr()), properties.loginPerMinute(),
					LOGIN_WINDOW);
		}
		else if (post && "/users".equals(path)) {
			retryAfterSeconds = acquire("register", clientKey(request.getRemoteAddr()),
					properties.registerPerHour(), REGISTER_WINDOW);
		}

		if (retryAfterSeconds > 0) {
			audit.warn("Limite de requisicoes excedido em {} pelo IP {}", path, request.getRemoteAddr());
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
			response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			response.getWriter().write("""
					{"type":"about:blank","title":"Too Many Requests","status":429,\
					"detail":"Muitas tentativas. Aguarde antes de tentar de novo."}""");
			return;
		}
		chain.doFilter(request, response);
	}

	/** Devolve 0 se a requisicao cabe no limite; senao, quantos segundos faltam para a janela reabrir. */
	long acquire(String route, String client, int limit, Duration window) {
		long now = clock.millis();
		long windowMillis = window.toMillis();
		sweepIfDue(now);

		String key = route + "|" + client;
		if (!windows.containsKey(key) && windows.size() >= MAX_TRACKED_KEYS) {
			key = route + "|" + OVERFLOW_CLIENT;
		}
		Window current = windows.compute(key, (k, w) -> (w == null || w.expired(now))
				? new Window(now, windowMillis, 1) : new Window(w.startMillis(), w.lengthMillis(), w.count() + 1));
		if (current.count() <= limit) {
			return 0;
		}
		return Math.max(1, (current.startMillis() + windowMillis - now + 999) / 1000);
	}

	int trackedKeys() {
		return windows.size();
	}

	// No maximo uma varredura a cada 30 segundos, feita por uma unica requisicao.
	private void sweepIfDue(long now) {
		long due = nextSweepMillis.get();
		if (now >= due && nextSweepMillis.compareAndSet(due, now + SWEEP_INTERVAL_MILLIS)) {
			windows.values().removeIf(window -> window.expired(now));
		}
	}

	/**
	 * Um cliente IPv6 costuma controlar um /64 inteiro, entao todos os enderecos do
	 * mesmo /64 contam como um so.
	 */
	static String clientKey(String remoteAddr) {
		if (remoteAddr == null || remoteAddr.indexOf(':') < 0) {
			return String.valueOf(remoteAddr);
		}
		try {
			// Literal de IP: nao faz consulta DNS.
			if (InetAddress.getByName(remoteAddr) instanceof Inet6Address address) {
				return HexFormat.of().formatHex(address.getAddress(), 0, 8) + "/64";
			}
		}
		catch (UnknownHostException ex) {
			// Cai no endereco como veio.
		}
		return remoteAddr;
	}

}
