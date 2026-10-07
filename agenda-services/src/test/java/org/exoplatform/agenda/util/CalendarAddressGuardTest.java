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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.meeds.commons.http.RefusedAddressException;
import io.meeds.commons.http.SafeFetchFailure;

/**
 * Pins what is a calendar link's own on top of the platform's guard
 * (EXO-90278): webcal read as https, and every refusal of the shared rules
 * reported as the reason the user is shown. The address ranges themselves are
 * pinned where the rules live, in commons.
 */
class CalendarAddressGuardTest {

  private static final Set<Integer> PORTS = Set.of(80, 443, 8080, 8443);

  /**
   * A guard with internal addresses refused and the default ports.
   *
   * @return the guard
   */
  private static CalendarAddressGuard guard() {
    return new CalendarAddressGuard(false, PORTS);
  }

  /**
   * The reason a URL is refused.
   *
   * @param url the URL
   * @return the reason
   */
  private static String refusal(String url) {
    return assertThrows(CalendarFeedException.class, () -> guard().normalize(url)).getReason();
  }

  /**
   * webcal and webcals are read as https, the fragment is dropped, and http and
   * https are kept as typed.
   *
   * @throws Exception never
   */
  @Test
  void webcalIsReadAsHttpsAndTheFragmentIsDropped() throws Exception {
    assertEquals("https://example.org/cal.ics", guard().normalize("webcal://example.org/cal.ics").toString());
    assertEquals("https://example.org/cal.ics", guard().normalize("  WEBCALS://example.org/cal.ics  ").toString());
    assertEquals("https://example.org/cal.ics?k=v", guard().normalize("https://example.org/cal.ics?k=v#frag").toString());
    assertEquals("http://example.org:8080/cal.ics", guard().normalize("HTTP://example.org:8080/cal.ics").toString());
  }

  /**
   * Each refusal of the shared rules is reported as the calendar link reason the
   * user is shown: the scheme, the credentials, the port, the shape, each from
   * the typed URL and from a redirect target alike.
   */
  @Test
  void eachRefusalIsReportedAsItsReason() {
    assertEquals(CalendarFeedException.SCHEME_NOT_ALLOWED, refusal("ftp://example.org/cal.ics"));
    assertEquals(CalendarFeedException.SCHEME_NOT_ALLOWED, refusal("file:///etc/passwd"));
    assertEquals(CalendarFeedException.CREDENTIALS_IN_URL, refusal("https://user:secret@example.org/cal.ics"));
    assertEquals(CalendarFeedException.PORT_NOT_ALLOWED, refusal("https://example.org:22/cal.ics"));
    assertEquals(CalendarFeedException.INVALID_URL, refusal(null));
    assertEquals(CalendarFeedException.INVALID_URL, refusal("not a url"));
    assertEquals(CalendarFeedException.INVALID_URL, refusal("https://example.org/a\nb"));
    assertEquals(CalendarFeedException.INVALID_URL, refusal("https://example.org/" + "a".repeat(CalendarAddressGuard.MAX_URL_LENGTH)));
    assertEquals(CalendarFeedException.PORT_NOT_ALLOWED,
                 assertThrows(CalendarFeedException.class, () -> guard().checkTarget(URI.create("http://example.org:6379/"))).getReason());
    CalendarAddressGuard narrow = new CalendarAddressGuard(false, Set.of(8443));
    assertEquals(CalendarFeedException.PORT_NOT_ALLOWED,
                 assertThrows(CalendarFeedException.class, () -> narrow.normalize("https://example.org/cal.ics")).getReason());
  }

  /**
   * Every failure the platform's fetcher reports has a calendar link reason, an
   * answer of a type that is not a calendar's reading as not a calendar.
   */
  @Test
  void everyFetchFailureHasAReason() {
    for (SafeFetchFailure failure : SafeFetchFailure.values()) {
      assertNotNull(CalendarAddressGuard.reason(failure), failure.name());
    }
    assertEquals(CalendarFeedException.NOT_A_CALENDAR, CalendarAddressGuard.reason(SafeFetchFailure.CONTENT_TYPE_NOT_ALLOWED));
    assertEquals(CalendarFeedException.INVALID_URL, CalendarAddressGuard.reason(SafeFetchFailure.INVALID_URL));
    assertEquals(CalendarFeedException.SCHEME_NOT_ALLOWED, CalendarAddressGuard.reason(SafeFetchFailure.SCHEME_NOT_ALLOWED));
    assertEquals(CalendarFeedException.PORT_NOT_ALLOWED, CalendarAddressGuard.reason(SafeFetchFailure.PORT_NOT_ALLOWED));
    assertEquals(CalendarFeedException.CREDENTIALS_IN_URL, CalendarAddressGuard.reason(SafeFetchFailure.CREDENTIALS_IN_URL));
    assertEquals(CalendarFeedException.REFUSED_ADDRESS, CalendarAddressGuard.reason(SafeFetchFailure.REFUSED_ADDRESS));
    assertEquals(CalendarFeedException.UNRESOLVABLE, CalendarAddressGuard.reason(SafeFetchFailure.UNRESOLVABLE));
    assertEquals(CalendarFeedException.TIMEOUT, CalendarAddressGuard.reason(SafeFetchFailure.TIMEOUT));
    assertEquals(CalendarFeedException.TOO_LARGE, CalendarAddressGuard.reason(SafeFetchFailure.TOO_LARGE));
    assertEquals(CalendarFeedException.TOO_MANY_REDIRECTS, CalendarAddressGuard.reason(SafeFetchFailure.TOO_MANY_REDIRECTS));
    assertEquals(CalendarFeedException.HTTP_ERROR, CalendarAddressGuard.reason(SafeFetchFailure.HTTP_ERROR));
    assertEquals(CalendarFeedException.UNREACHABLE, CalendarAddressGuard.reason(SafeFetchFailure.UNREACHABLE));
  }

  /**
   * The addresses are the platform's rules, applied with the deployment's
   * opt-out: an internal literal is refused through the shared guard, and read
   * when the deployment allows internal addresses.
   *
   * @throws Exception never
   */
  @Test
  void addressesAreJudgedByThePlatformsGuardWithTheDeploymentsOptOut() throws Exception {
    assertThrows(RefusedAddressException.class, () -> guard().getDelegate().resolveAllowed("127.0.0.1"));
    assertThrows(RefusedAddressException.class, () -> guard().getDelegate().resolveAllowed("169.254.169.254"));
    assertThrows(RefusedAddressException.class, () -> guard().getDelegate().resolveAllowed("[::1]"));
    new CalendarAddressGuard(true, PORTS).getDelegate().resolveAllowed("127.0.0.1");
  }

}
