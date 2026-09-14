/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Runs the calendar link fetcher against a stub HTTP server in the test JVM
 * (EXO-90278) — nothing leaves the machine.
 * <p>
 * <b>How a loopback server stands in for a public one.</b> The server listens
 * on 127.0.0.1. Host names are resolved from a table, and the guard exempts
 * exactly 127.0.0.1 from the internal-address refusal; every other internal
 * address — 127.0.0.2, 10.0.0.5 — stays refused. A refused name therefore
 * resolves to such an address, and the stub's request log proves no request
 * reached it: on the machine 127.0.0.2 either has no listener at all or reaches
 * nothing but this stub, and the log is empty either way.
 */
class CalendarFeedFetcherTest {

  private static final String       CALENDAR = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n";

  private final Map<String, InetAddress[]> dns = new HashMap<>();

  private final List<String>        hits     = new CopyOnWriteArrayList<>();

  private final List<Headers>       requests = new CopyOnWriteArrayList<>();

  private volatile HttpHandler      handler;

  private HttpServer                server;

  private ExecutorService           executor;

  private InetAddress               stub;

  private int                       port;

  private CalendarFeedFetcher       fetcher;

  /**
   * Starts the stub on loopback and maps the test names.
   *
   * @throws Exception when the server cannot start
   */
  @BeforeEach
  void startServer() throws Exception {
    stub = InetAddress.getByAddress("public.test", new byte[] { 127, 0, 0, 1 });
    server = HttpServer.create(new InetSocketAddress(stub, 0), 0);
    executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.createContext("/", exchange -> {
      hits.add(exchange.getRequestURI().getPath());
      requests.add(exchange.getRequestHeaders());
      try {
        handler.handle(exchange);
      } catch (IOException e) {
        // the client went away, as a limit test intends
      } finally {
        exchange.close();
      }
    });
    server.start();
    port = server.getAddress().getPort();
    dns.put("public.test", new InetAddress[] { stub });
    dns.put("private.test", new InetAddress[] { InetAddress.getByAddress(new byte[] { 127, 0, 0, 2 }) });
    dns.put("internal.test", new InetAddress[] { InetAddress.getByAddress(new byte[] { 10, 0, 0, 5 }) });
    fetcher = fetcher(false, 1024 * 1024, Duration.ofSeconds(2), Duration.ofSeconds(5), 3);
  }

  /**
   * Stops the stub and the fetcher.
   */
  @AfterEach
  void stopServer() {
    fetcher.close();
    server.stop(0);
    executor.shutdownNow();
  }

  /**
   * A fetcher over the table of names, exempting the stub's address.
   *
   * @param allowInternal the deployment's opt-out
   * @param maxBytes the body limit
   * @param readTimeout the read timeout
   * @param totalTimeout the deadline
   * @param maxRedirects the redirect limit
   * @return the fetcher
   */
  private CalendarFeedFetcher fetcher(boolean allowInternal, long maxBytes, Duration readTimeout, Duration totalTimeout, int maxRedirects) {
    CalendarAddressGuard guard = new CalendarAddressGuard(allowInternal, Set.of(port), this::resolve, Set.of(stub));
    return new CalendarFeedFetcher(guard, maxBytes, Duration.ofSeconds(2), readTimeout, totalTimeout, maxRedirects);
  }

  /**
   * Resolves a name from the table.
   *
   * @param host the name
   * @return its addresses
   * @throws UnknownHostException when the table does not know it
   */
  private InetAddress[] resolve(String host) throws UnknownHostException {
    InetAddress[] addresses = dns.get(host);
    if (addresses == null) {
      throw new UnknownHostException(host);
    }
    return addresses;
  }

  /**
   * A URL on a test name, at the stub's port.
   *
   * @param host the test name
   * @param path the path
   * @return the URL
   */
  private URI url(String host, String path) {
    return URI.create("http://" + host + ":" + port + path);
  }

  /**
   * Answers a body.
   *
   * @param exchange the exchange
   * @param status the status
   * @param body the body
   * @throws IOException when the client went away
   */
  private static void answer(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      try (OutputStream output = exchange.getResponseBody()) {
        output.write(bytes);
      }
    }
  }

  /**
   * The reason a read fails.
   *
   * @param fetcherUsed the fetcher
   * @param uri the URL
   * @return the reason
   */
  private static String failure(CalendarFeedFetcher fetcherUsed, URI uri) {
    return assertThrows(CalendarFeedException.class, () -> fetcherUsed.fetch(uri, null, null)).getReason();
  }

  /**
   * A link answering a calendar gives its body and its validators.
   *
   * @throws Exception when the read fails
   */
  @Test
  void aCalendarIsReadWithItsValidators() throws Exception {
    handler = exchange -> {
      exchange.getResponseHeaders().add("ETag", "\"v1\"");
      exchange.getResponseHeaders().add("Last-Modified", "Mon, 14 Sep 2026 10:00:00 GMT");
      answer(exchange, 200, CALENDAR);
    };

    CalendarFeedFetcher.FeedResponse response = fetcher.fetch(url("public.test", "/cal.ics"), null, null);

    assertFalse(response.notModified());
    assertArrayEquals(CALENDAR.getBytes(StandardCharsets.UTF_8), response.body());
    assertEquals("\"v1\"", response.etag());
    assertEquals("Mon, 14 Sep 2026 10:00:00 GMT", response.lastModified());
  }

  /**
   * A validator longer than the column that stores it is not kept: a
   * Last-Modified of 129 characters would make recording the refresh fail on
   * every read.
   *
   * @throws Exception when the read fails
   */
  @Test
  void aValidatorLongerThanItsColumnIsNotKept() throws Exception {
    handler = exchange -> {
      exchange.getResponseHeaders().add("ETag", "\"" + "e".repeat(CalendarFeedFetcher.MAX_ETAG) + "\"");
      exchange.getResponseHeaders().add("Last-Modified", "L".repeat(CalendarFeedFetcher.MAX_LAST_MODIFIED + 1));
      answer(exchange, 200, CALENDAR);
    };

    CalendarFeedFetcher.FeedResponse response = fetcher.fetch(url("public.test", "/cal.ics"), null, null);

    assertNull(response.etag());
    assertNull(response.lastModified());
    assertArrayEquals(CALENDAR.getBytes(StandardCharsets.UTF_8), response.body());
  }

  /**
   * The validators of the previous read are sent, and a 304 is read as nothing
   * changed.
   *
   * @throws Exception when the read fails
   */
  @Test
  void aConditionalReadSendsTheValidatorsAndReadsNotModified() throws Exception {
    handler = exchange -> {
      Headers headers = exchange.getRequestHeaders();
      if ("\"v1\"".equals(headers.getFirst("If-None-Match"))
          && "Mon, 14 Sep 2026 10:00:00 GMT".equals(headers.getFirst("If-Modified-Since"))) {
        answer(exchange, 304, "");
      } else {
        answer(exchange, 200, CALENDAR);
      }
    };

    CalendarFeedFetcher.FeedResponse response = fetcher.fetch(url("public.test", "/cal.ics"),
                                                              "\"v1\"",
                                                              "Mon, 14 Sep 2026 10:00:00 GMT");

    assertTrue(response.notModified(), "the server said nothing changed");
    assertNull(response.body());
  }

  /**
   * Nothing of the platform's goes out: a cookie set by a feed is never sent
   * back, and no credentials are sent.
   *
   * @throws Exception when a read fails
   */
  @Test
  void noCookieAndNoCredentialsAreSent() throws Exception {
    handler = exchange -> {
      exchange.getResponseHeaders().add("Set-Cookie", "session=secret; Path=/");
      answer(exchange, 200, CALENDAR);
    };

    fetcher.fetch(url("public.test", "/cal.ics"), null, null);
    fetcher.fetch(url("public.test", "/cal.ics"), null, null);

    assertEquals(2, requests.size());
    for (Headers headers : requests) {
      assertNull(headers.getFirst("Cookie"), "no cookie is sent back");
      assertNull(headers.getFirst("Authorization"), "no credentials are sent");
      assertNull(headers.getFirst("Proxy-Authorization"), "no proxy credentials are sent");
    }
  }

  /**
   * A redirect to an internal address is refused, and the request never reaches
   * it: the stub saw the first request only.
   */
  @Test
  void aRedirectToAnInternalAddressIsRefusedAndNeverReached() {
    handler = exchange -> {
      if ("/start".equals(exchange.getRequestURI().getPath())) {
        exchange.getResponseHeaders().add("Location", "http://private.test:" + port + "/secret");
        answer(exchange, 302, "");
      } else {
        answer(exchange, 200, CALENDAR);
      }
    };

    assertEquals(CalendarFeedException.REFUSED_ADDRESS, failure(fetcher, url("public.test", "/start")));
    assertEquals(List.of("/start"), hits, "the redirect target must never be requested");
  }

  /**
   * A redirect whose target breaks the URL rules — a port outside the allowed
   * set, a scheme other than http — is refused like a typed link would be, and
   * never requested.
   */
  @Test
  void aRedirectToAPortOrSchemeOutsideTheRulesIsRefusedAndNeverReached() {
    handler = exchange -> {
      String requested = exchange.getRequestURI().getPath();
      if ("/port".equals(requested)) {
        exchange.getResponseHeaders().add("Location", "http://public.test:22/secret");
        answer(exchange, 302, "");
      } else if ("/scheme".equals(requested)) {
        exchange.getResponseHeaders().add("Location", "file:///etc/passwd");
        answer(exchange, 302, "");
      } else {
        answer(exchange, 200, CALENDAR);
      }
    };

    assertEquals(CalendarFeedException.PORT_NOT_ALLOWED, failure(fetcher, url("public.test", "/port")));
    assertEquals(CalendarFeedException.SCHEME_NOT_ALLOWED, failure(fetcher, url("public.test", "/scheme")));
    assertEquals(List.of("/port", "/scheme"), hits, "no redirect target may be requested");
  }

  /**
   * The control of the refusal above: the same redirect, to an address that is
   * allowed, is followed.
   *
   * @throws Exception when the read fails
   */
  @Test
  void aRedirectToAnAllowedAddressIsFollowed() throws Exception {
    dns.put("private.test", new InetAddress[] { stub });
    handler = exchange -> {
      if ("/start".equals(exchange.getRequestURI().getPath())) {
        exchange.getResponseHeaders().add("Location", "http://private.test:" + port + "/secret");
        answer(exchange, 302, "");
      } else {
        answer(exchange, 200, CALENDAR);
      }
    };

    fetcher.fetch(url("public.test", "/start"), null, null);

    assertEquals(List.of("/start", "/secret"), hits);
  }

  /**
   * A name that resolved to an allowed address when it was checked and to an
   * internal one when the connection is opened — DNS rebinding — is refused at
   * the connection: the address the client dials is the one judged.
   */
  @Test
  void aNameRebindingToAnInternalAddressIsRefusedAtConnection() throws Exception {
    InetAddress internal = InetAddress.getByAddress(new byte[] { 127, 0, 0, 2 });
    AtomicInteger lookups = new AtomicInteger();
    CalendarAddressGuard guard = new CalendarAddressGuard(false, Set.of(port), host -> {
      if (!"rebind.test".equals(host)) {
        throw new UnknownHostException(host);
      }
      return lookups.incrementAndGet() == 1 ? new InetAddress[] { stub } : new InetAddress[] { internal };
    }, Set.of(stub));
    CalendarFeedFetcher rebinding = new CalendarFeedFetcher(guard, 1024, Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(5), 3);
    handler = exchange -> answer(exchange, 200, CALENDAR);
    try {
      assertEquals(CalendarFeedException.REFUSED_ADDRESS, failure(rebinding, url("rebind.test", "/cal.ics")));
    } finally {
      rebinding.close();
    }
    assertTrue(lookups.get() >= 2, "the connection must resolve the name again rather than trust the first answer");
    assertTrue(hits.isEmpty(), "no request may reach the stub through a name judged internal");
  }

  /**
   * A name resolving to a private address is refused before any connection.
   */
  @Test
  void aNameResolvingToAPrivateAddressIsRefusedBeforeAnyRequest() {
    handler = exchange -> answer(exchange, 200, CALENDAR);
    long start = System.nanoTime();

    assertEquals(CalendarFeedException.REFUSED_ADDRESS, failure(fetcher, url("internal.test", "/cal.ics")));

    assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 1500, "a refusal must not wait for a connection");
    assertTrue(hits.isEmpty());
  }

  /**
   * The deployment's opt-out lets an internal address be read.
   *
   * @throws Exception when the read fails
   */
  @Test
  void internalAddressesAreReadWhenTheDeploymentAllowsThem() throws Exception {
    dns.put("loopback.test", new InetAddress[] { InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }) });
    handler = exchange -> answer(exchange, 200, CALENDAR);
    CalendarAddressGuard refusing = new CalendarAddressGuard(false, Set.of(port), this::resolve, Set.of());
    CalendarFeedFetcher closed = new CalendarFeedFetcher(refusing, 1024, Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(5), 3);
    CalendarAddressGuard allowing = new CalendarAddressGuard(true, Set.of(port), this::resolve, Set.of());
    CalendarFeedFetcher open = new CalendarFeedFetcher(allowing, 1024, Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(5), 3);
    try {
      assertEquals(CalendarFeedException.REFUSED_ADDRESS, failure(closed, url("loopback.test", "/cal.ics")));
      assertTrue(hits.isEmpty());
      assertArrayEquals(CALENDAR.getBytes(StandardCharsets.UTF_8), open.fetch(url("loopback.test", "/cal.ics"), null, null).body());
    } finally {
      closed.close();
      open.close();
    }
  }

  /**
   * A name resolving to nothing is reported as such.
   */
  @Test
  void anUnknownNameIsUnresolvable() {
    assertEquals(CalendarFeedException.UNRESOLVABLE, failure(fetcher, url("nowhere.test", "/cal.ics")));
  }

  /**
   * A redirect loop stops at the limit.
   */
  @Test
  void redirectsStopAtTheLimit() {
    handler = exchange -> {
      exchange.getResponseHeaders().add("Location", "/loop");
      answer(exchange, 302, "");
    };

    assertEquals(CalendarFeedException.TOO_MANY_REDIRECTS, failure(fetcher, url("public.test", "/loop")));
    assertEquals(4, hits.size(), "the first request and three redirects, then nothing");
  }

  /**
   * A body over the limit is refused, announced or streamed.
   */
  @Test
  void aBodyOverTheLimitIsRefused() {
    CalendarFeedFetcher small = fetcher(false, 1024, Duration.ofSeconds(2), Duration.ofSeconds(5), 3);
    try {
      handler = exchange -> answer(exchange, 200, "X".repeat(2048));
      assertEquals(CalendarFeedException.TOO_LARGE, failure(small, url("public.test", "/announced")));

      handler = exchange -> {
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream output = exchange.getResponseBody()) {
          for (int i = 0; i < 8; i++) {
            output.write("Y".repeat(512).getBytes(StandardCharsets.UTF_8));
            output.flush();
          }
        }
      };
      assertEquals(CalendarFeedException.TOO_LARGE, failure(small, url("public.test", "/streamed")));
    } finally {
      small.close();
    }
  }

  /**
   * A server that does not answer in time gives a timeout.
   */
  @Test
  void aSilentServerTimesOut() {
    CalendarFeedFetcher impatient = fetcher(false, 1024, Duration.ofMillis(300), Duration.ofSeconds(5), 3);
    handler = exchange -> {
      sleep(2000);
      answer(exchange, 200, CALENDAR);
    };
    try {
      assertEquals(CalendarFeedException.TIMEOUT, failure(impatient, url("public.test", "/slow")));
    } finally {
      impatient.close();
    }
  }

  /**
   * A server trickling bytes, each within the read timeout, is stopped by the
   * deadline over the whole read.
   */
  @Test
  void aTricklingServerIsStoppedByTheDeadline() {
    CalendarFeedFetcher bounded = fetcher(false, 1024 * 1024, Duration.ofSeconds(1), Duration.ofMillis(700), 3);
    handler = exchange -> {
      exchange.sendResponseHeaders(200, 0);
      try (OutputStream output = exchange.getResponseBody()) {
        for (int i = 0; i < 30; i++) {
          output.write('B');
          output.flush();
          sleep(100);
        }
      }
    };
    long start = System.nanoTime();
    try {
      assertEquals(CalendarFeedException.TIMEOUT, failure(bounded, url("public.test", "/trickle")));
    } finally {
      bounded.close();
    }
    assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2500, "the deadline must cut the read short");
  }

  /**
   * An error status is reported as such.
   */
  @Test
  void anErrorStatusIsReported() {
    handler = exchange -> answer(exchange, 500, "oops");
    assertEquals(CalendarFeedException.HTTP_ERROR, failure(fetcher, url("public.test", "/error")));
    handler = exchange -> answer(exchange, 404, "");
    assertEquals(CalendarFeedException.HTTP_ERROR, failure(fetcher, url("public.test", "/missing")));
  }

  /**
   * Sleeps, interrupted or not.
   *
   * @param millis how long
   */
  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

}
