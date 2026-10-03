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

import java.net.URI;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.meeds.commons.http.SafeFetchException;
import io.meeds.commons.http.SafeFetchPolicy;
import io.meeds.commons.http.SafeFetchRequest;
import io.meeds.commons.http.SafeFetchResponse;
import io.meeds.commons.http.SafeHttpFetcher;

import jakarta.annotation.PreDestroy;

/**
 * Reads a calendar link over HTTP (EXO-90278), within bounds, through the
 * platform's {@link SafeHttpFetcher}.
 * <p>
 * <b>Addresses</b> are the shared guard's: it is the resolver of the HTTP
 * client's connection manager, so the address of every connection is judged
 * when the connection is opened, redirects followed one hop at a time with
 * each target checked again. <b>Bounds</b>: a connect timeout, a read timeout,
 * a deadline over the whole read — redirects included — a body limit counted on
 * the decoded bytes, and a redirect count. <b>Nothing of the platform's goes
 * out</b>: no cookie store, no credentials, no proxy, no retry; the only
 * headers sent are the ones a conditional read needs. <b>Nothing of the URL
 * goes into a log</b>: it may embed a secret.
 */
@Component
public class CalendarFeedFetcher {

  /** Largest body read, in bytes. */
  public static final long           DEFAULT_MAX_BYTES     = 5L * 1024 * 1024;

  /** Longest wait for a connection. */
  public static final Duration       DEFAULT_CONNECT       = Duration.ofSeconds(10);

  /** Longest wait between two reads. */
  public static final Duration       DEFAULT_READ          = Duration.ofSeconds(20);

  /** Longest read of a link, redirects included. */
  public static final Duration       DEFAULT_TOTAL         = Duration.ofSeconds(30);

  /** Most redirects followed. */
  public static final int            DEFAULT_MAX_REDIRECTS = 5;

  /** The ports a link may reach unless the deployment says otherwise. */
  public static final String         DEFAULT_PORTS         = "80,443,8080,8443";

  /** Longest entity tag kept: the width of {@code EXO_AGENDA_SUBSCRIPTION.ETAG}. */
  static final int                   MAX_ETAG              = 512;

  /** Longest Last-Modified kept: the width of {@code EXO_AGENDA_SUBSCRIPTION.LAST_MODIFIED}. */
  static final int                   MAX_LAST_MODIFIED     = 128;

  private static final String        USER_AGENT            = "eXo-Agenda-Calendar-Subscription/1.0";

  private static final String        ACCEPT                = "text/calendar, text/plain;q=0.5, */*;q=0.1";

  private final SafeHttpFetcher      fetcher;

  private final CalendarAddressGuard guard;

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
    this(SafeFetchPolicy.builder()
                        .name("agenda-calendar-subscription")
                        .userAgent(USER_AGENT)
                        .internalAddressesAllowed(allowInternalAddresses)
                        .allowedPorts(allowedPorts, SafeFetchPolicy.DEFAULT_PORTS)
                        .maxBytes(DEFAULT_MAX_BYTES)
                        .maxRedirects(DEFAULT_MAX_REDIRECTS)
                        .connectTimeout(DEFAULT_CONNECT)
                        .readTimeout(DEFAULT_READ)
                        .totalTimeout(DEFAULT_TOTAL)
                        .build());
  }

  /**
   * A fetcher under a policy handed in: the deployment's, or a test's, with its
   * name table and bounds.
   *
   * @param policy what may be read and how far the read goes
   */
  CalendarFeedFetcher(SafeFetchPolicy policy) {
    this.fetcher = new SafeHttpFetcher(policy);
    this.guard = new CalendarAddressGuard(fetcher.getGuard());
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
    SafeFetchResponse response;
    try {
      response = fetcher.fetch(SafeFetchRequest.get(uri).withAccept(ACCEPT).withValidators(etag, lastModified));
    } catch (SafeFetchException e) {
      throw new CalendarFeedException(CalendarAddressGuard.reason(e.getFailure()), e);
    }
    String answeredEtag = header(response, "ETag", MAX_ETAG);
    String answeredLastModified = header(response, "Last-Modified", MAX_LAST_MODIFIED);
    if (response.notModified()) {
      return new FeedResponse(true, null, answeredEtag, answeredLastModified);
    }
    if (response.body().length == 0) {
      throw new CalendarFeedException(CalendarFeedException.NOT_A_CALENDAR);
    }
    return new FeedResponse(false, response.body(), answeredEtag, answeredLastModified);
  }

  /**
   * Closes the connections.
   */
  @PreDestroy
  public void close() {
    fetcher.close();
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
  private static String header(SafeFetchResponse response, String name, int maxLength) {
    String value = response.header(name);
    if (StringUtils.isBlank(value) || value.length() > maxLength) {
      return null;
    }
    return value;
  }

}
