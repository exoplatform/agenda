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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import jakarta.annotation.PreDestroy;

/**
 * Reads a calendar link over HTTP (EXO-90278), within bounds.
 * <p>
 * <b>Addresses</b> are the {@link CalendarAddressGuard}'s: it is the resolver
 * of the connection manager, so the address of every connection is judged when
 * the connection is opened; the guard also checks each URL's shape before it is
 * requested, and redirects are followed here, one hop at a time, each target
 * checked again. <b>Bounds</b>: a connect timeout, a read timeout, a deadline
 * over the whole read — redirects included — enforced by cancelling the request,
 * a body limit counted on the decoded bytes (a compressed body cannot inflate
 * past it), and a redirect count. <b>Nothing of the platform's goes out</b>: no
 * cookie store, no credentials, no proxy, no retry; the only headers sent are
 * the ones a conditional read needs. <b>Nothing of the URL goes into a log</b>:
 * it may embed a secret.
 */
@Component
public class CalendarFeedFetcher {

  /** Largest body read, in bytes. */
  public static final long      DEFAULT_MAX_BYTES     = 5L * 1024 * 1024;

  /** Longest wait for a connection. */
  public static final Duration  DEFAULT_CONNECT       = Duration.ofSeconds(10);

  /** Longest wait between two reads. */
  public static final Duration  DEFAULT_READ          = Duration.ofSeconds(20);

  /** Longest read of a link, redirects included. */
  public static final Duration  DEFAULT_TOTAL         = Duration.ofSeconds(30);

  /** Most redirects followed. */
  public static final int       DEFAULT_MAX_REDIRECTS = 5;

  /** The ports a link may reach unless the deployment says otherwise. */
  public static final String    DEFAULT_PORTS         = "80,443,8080,8443";

  /** Longest entity tag kept: the width of {@code EXO_AGENDA_SUBSCRIPTION.ETAG}. */
  static final int              MAX_ETAG              = 512;

  /** Longest Last-Modified kept: the width of {@code EXO_AGENDA_SUBSCRIPTION.LAST_MODIFIED}. */
  static final int              MAX_LAST_MODIFIED     = 128;

  private static final String   USER_AGENT            = "eXo-Agenda-Calendar-Subscription/1.0";

  private static final String   ACCEPT                = "text/calendar, text/plain;q=0.5, */*;q=0.1";

  private static final Log      LOG                   = ExoLogger.getLogger(CalendarFeedFetcher.class);

  private final CalendarAddressGuard guard;

  private final long            maxBytes;

  private final Duration        totalTimeout;

  private final int             maxRedirects;

  private final CloseableHttpClient httpClient;

  private final ScheduledExecutorService deadlines;

  /**
   * What a read of a link answered.
   *
   * @param notModified whether the server answered that nothing changed since
   *          the validators sent
   * @param body the body read, null when not modified
   * @param etag the entity tag answered, null when none
   * @param lastModified the Last-Modified header answered, null when none
   */
  public record FeedResponse(boolean notModified, byte[] body, String etag, String lastModified) {
  }

  /**
   * The fetcher of a deployment.
   *
   * @param allowInternalAddresses whether internal addresses may be read, false
   *          by default: the deployment's opt-out, as caldav-integration's
   *          {@code exo.agenda.caldav.server.allowPrivateAddresses}
   * @param allowedPorts comma-separated ports a link may reach
   */
  @Autowired
  public CalendarFeedFetcher(@Value("${exo.agenda.calendarSubscription.allowInternalAddresses:false}")
                             boolean allowInternalAddresses,
                             @Value("${exo.agenda.calendarSubscription.allowedPorts:" + DEFAULT_PORTS + "}")
                             String allowedPorts) {
    this(new CalendarAddressGuard(allowInternalAddresses, parsePorts(allowedPorts)),
         DEFAULT_MAX_BYTES,
         DEFAULT_CONNECT,
         DEFAULT_READ,
         DEFAULT_TOTAL,
         DEFAULT_MAX_REDIRECTS);
  }

  /**
   * A fetcher with its guard and bounds handed in, for the tests.
   *
   * @param guard the address guard
   * @param maxBytes largest body read
   * @param connectTimeout longest wait for a connection
   * @param readTimeout longest wait between two reads
   * @param totalTimeout longest read of a link
   * @param maxRedirects most redirects followed
   */
  CalendarFeedFetcher(CalendarAddressGuard guard,
                      long maxBytes,
                      Duration connectTimeout,
                      Duration readTimeout,
                      Duration totalTimeout,
                      int maxRedirects) {
    this.guard = guard;
    this.maxBytes = maxBytes;
    this.totalTimeout = totalTimeout;
    this.maxRedirects = maxRedirects;
    DnsResolver resolver = new DnsResolver() {
      @Override
      public InetAddress[] resolve(String host) throws UnknownHostException {
        return guard.resolveAllowed(host);
      }

      @Override
      public String resolveCanonicalHostname(String host) throws UnknownHostException {
        guard.resolveAllowed(host);
        return host;
      }
    };
    this.httpClient = HttpClients.custom()
                                 .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                                                                                                .setDnsResolver(resolver)
                                                                                                .setDefaultConnectionConfig(ConnectionConfig.custom()
                                                                                                                                            .setConnectTimeout(Timeout.of(connectTimeout))
                                                                                                                                            .setSocketTimeout(Timeout.of(readTimeout))
                                                                                                                                            .build())
                                                                                                .setMaxConnTotal(20)
                                                                                                .setMaxConnPerRoute(2)
                                                                                                .build())
                                 .setDefaultRequestConfig(RequestConfig.custom()
                                                                       .setRedirectsEnabled(false)
                                                                       .setResponseTimeout(Timeout.of(readTimeout))
                                                                       .setConnectionRequestTimeout(Timeout.of(connectTimeout))
                                                                       .build())
                                 .disableRedirectHandling()
                                 .disableCookieManagement()
                                 .disableAuthCaching()
                                 .disableAutomaticRetries()
                                 .setUserAgent(USER_AGENT)
                                 .build();
    this.deadlines = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "agenda-calendar-subscription-deadline");
      thread.setDaemon(true);
      return thread;
    });
  }

  /**
   * @return the address guard, whose URL rules the service applies before a
   *         read
   */
  public CalendarAddressGuard getGuard() {
    return guard;
  }

  /**
   * Reads a link, conditionally when validators are given.
   *
   * @param uri the URL, already normalized by the guard
   * @param etag the entity tag of the previous read, or null
   * @param lastModified the Last-Modified header of the previous read, or null
   * @return what the server answered
   * @throws CalendarFeedException with the reason nothing usable was read
   */
  public FeedResponse fetch(URI uri, String etag, String lastModified) throws CalendarFeedException {
    long deadline = System.nanoTime() + totalTimeout.toNanos();
    URI current = guard.checkTarget(uri);
    for (int hop = 0;; hop++) {
      Answer answer = request(current, etag, lastModified, deadline);
      if (answer.redirect() == null) {
        return answer.response();
      }
      if (hop >= maxRedirects) {
        throw new CalendarFeedException(CalendarFeedException.TOO_MANY_REDIRECTS);
      }
      current = guard.checkTarget(redirectTarget(current, answer.redirect()));
    }
  }

  /**
   * Stops the deadline thread and closes the connections.
   */
  @PreDestroy
  public void close() {
    deadlines.shutdownNow();
    try {
      httpClient.close();
    } catch (IOException e) {
      LOG.debug("The calendar subscription HTTP client did not close cleanly", e);
    }
  }

  /**
   * One request of a read: the address checked before it is requested — the
   * resolver checks it again at connect time — the deadline armed, the answer
   * handled.
   *
   * @param uri the URL of this hop
   * @param etag the entity tag to send, or null
   * @param lastModified the date to send, or null
   * @param deadline the read's deadline, as {@link System#nanoTime()}
   * @return the answer: a response, or a redirect to follow
   * @throws CalendarFeedException with the reason nothing usable was read
   */
  private Answer request(URI uri, String etag, String lastModified, long deadline) throws CalendarFeedException {
    try {
      guard.resolveAllowed(uri.getHost());
    } catch (CalendarAddressGuard.RefusedAddressException e) {
      throw new CalendarFeedException(CalendarFeedException.REFUSED_ADDRESS);
    } catch (UnknownHostException e) {
      throw new CalendarFeedException(CalendarFeedException.UNRESOLVABLE);
    }
    long remaining = deadline - System.nanoTime();
    if (remaining <= 0) {
      throw new CalendarFeedException(CalendarFeedException.TIMEOUT);
    }
    HttpGet get = new HttpGet(uri);
    get.setHeader(HttpHeaders.ACCEPT, ACCEPT);
    if (StringUtils.isNotBlank(etag)) {
      get.setHeader(HttpHeaders.IF_NONE_MATCH, etag);
    }
    if (StringUtils.isNotBlank(lastModified)) {
      get.setHeader(HttpHeaders.IF_MODIFIED_SINCE, lastModified);
    }
    ScheduledFuture<?> timer = deadlines.schedule(get::cancel, remaining, TimeUnit.NANOSECONDS);
    try {
      return httpClient.execute(get, response -> handle(response, deadline));
    } catch (FeedIOException e) {
      throw new CalendarFeedException(e.reason, e);
    } catch (IOException | RuntimeException e) {
      String reason = System.nanoTime() >= deadline || get.isCancelled() ? CalendarFeedException.TIMEOUT : reasonOf(e);
      LOG.debug("A calendar link could not be read: {} ({})", reason, e.getClass().getSimpleName());
      throw new CalendarFeedException(reason, e);
    } finally {
      timer.cancel(false);
    }
  }

  /**
   * Reads an answer: nothing modified, a redirect to follow, a body within the
   * limit, or the failure it is.
   *
   * @param response the answer
   * @param deadline the read's deadline
   * @return the answer read
   * @throws IOException carrying the reason of a failure
   */
  private Answer handle(ClassicHttpResponse response, long deadline) throws IOException {
    int status = response.getCode();
    if (status == 304) {
      return new Answer(new FeedResponse(true,
                                         null,
                                         header(response, HttpHeaders.ETAG, MAX_ETAG),
                                         header(response, HttpHeaders.LAST_MODIFIED, MAX_LAST_MODIFIED)),
                        null);
    }
    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
      String location = header(response, HttpHeaders.LOCATION, Integer.MAX_VALUE);
      if (StringUtils.isBlank(location)) {
        throw new FeedIOException(CalendarFeedException.HTTP_ERROR);
      }
      return new Answer(null, location);
    }
    if (status < 200 || status >= 300) {
      throw new FeedIOException(CalendarFeedException.HTTP_ERROR);
    }
    HttpEntity entity = response.getEntity();
    if (entity == null) {
      throw new FeedIOException(CalendarFeedException.NOT_A_CALENDAR);
    }
    if (entity.getContentLength() > maxBytes) {
      throw new FeedIOException(CalendarFeedException.TOO_LARGE);
    }
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    try (InputStream input = entity.getContent()) {
      byte[] buffer = new byte[8192];
      long total = 0;
      int read;
      while ((read = input.read(buffer)) != -1) {
        total += read;
        if (total > maxBytes) {
          throw new FeedIOException(CalendarFeedException.TOO_LARGE);
        }
        if (System.nanoTime() >= deadline) {
          throw new FeedIOException(CalendarFeedException.TIMEOUT);
        }
        body.write(buffer, 0, read);
      }
    }
    return new Answer(new FeedResponse(false,
                                       body.toByteArray(),
                                       header(response, HttpHeaders.ETAG, MAX_ETAG),
                                       header(response, HttpHeaders.LAST_MODIFIED, MAX_LAST_MODIFIED)),
                      null);
  }

  /**
   * The URL a redirect points at, a relative Location resolved against the URL
   * that answered.
   *
   * @param current the URL that answered
   * @param location the Location header
   * @return the URL to read next
   * @throws CalendarFeedException when the Location is not a URL
   */
  private URI redirectTarget(URI current, String location) throws CalendarFeedException {
    try {
      return current.resolve(new URI(location.trim()));
    } catch (URISyntaxException | IllegalArgumentException e) {
      throw new CalendarFeedException(CalendarFeedException.INVALID_URL);
    }
  }

  /**
   * The reason of a failure the HTTP client threw, from the exception or its
   * causes.
   *
   * @param failure what was thrown
   * @return the reason
   */
  private static String reasonOf(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof CalendarAddressGuard.RefusedAddressException) {
        return CalendarFeedException.REFUSED_ADDRESS;
      }
      if (cause instanceof UnknownHostException) {
        return CalendarFeedException.UNRESOLVABLE;
      }
      if (cause instanceof InterruptedIOException) {
        return CalendarFeedException.TIMEOUT;
      }
      if (cause.getCause() == cause) {
        break;
      }
    }
    return CalendarFeedException.UNREACHABLE;
  }

  /**
   * The value of a validator header, null when absent, blank, or longer than the
   * column that stores it: a validator cut short would never match again, and a
   * value over the column would make recording the refresh fail.
   *
   * @param response the answer
   * @param name the header name
   * @param maxLength the longest value kept
   * @return the value
   */
  private static String header(ClassicHttpResponse response, String name, int maxLength) {
    Header header = response.getFirstHeader(name);
    if (header == null || StringUtils.isBlank(header.getValue()) || header.getValue().length() > maxLength) {
      return null;
    }
    return header.getValue();
  }

  /**
   * Reads a comma-separated list of ports, ignoring what is not one.
   *
   * @param ports the list
   * @return the ports
   */
  static Set<Integer> parsePorts(String ports) {
    Set<Integer> parsed = new LinkedHashSet<>();
    Arrays.stream(StringUtils.split(StringUtils.defaultString(ports), ','))
          .map(String::trim)
          .filter(StringUtils::isNumeric)
          .map(Integer::parseInt)
          .filter(port -> port > 0 && port <= 65535)
          .forEach(parsed::add);
    return parsed;
  }

  /**
   * One request's outcome: a response, or the Location of a redirect.
   *
   * @param response the response, null for a redirect
   * @param redirect the Location, null for a response
   */
  private record Answer(FeedResponse response, String redirect) {
  }

  /**
   * A failure met while handling an answer, carried out of the HTTP client's
   * handler as the {@link IOException} it may throw.
   */
  private static final class FeedIOException extends IOException {

    private static final long serialVersionUID = 1392170498624906212L;

    private final String      reason;

    /**
     * Builds the failure.
     *
     * @param reason the reason constant
     */
    FeedIOException(String reason) {
      super(reason);
      this.reason = reason;
    }
  }

}
