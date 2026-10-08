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
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;

import io.meeds.commons.http.SafeAddressGuard;
import io.meeds.commons.http.SafeFetchException;
import io.meeds.commons.http.SafeFetchFailure;

/**
 * Decides which calendar links the platform may be made to read (EXO-90278):
 * the platform's {@link SafeAddressGuard}, with what is a calendar's own —
 * {@code webcal://} and {@code webcals://} read as {@code https://}, and each
 * refusal reported as the {@link CalendarFeedException} reason the user is
 * shown.
 * <p>
 * The rules themselves — http or https, no credentials, a port in the allowed
 * set, and every internal address range refused unless the deployment allows
 * internal addresses — are the shared guard's, applied once more by the HTTP
 * client's own resolver when a connection opens, so that the address checked
 * is the address dialled.
 */
public class CalendarAddressGuard {

  /** Longest URL accepted. */
  public static final int        MAX_URL_LENGTH = SafeAddressGuard.MAX_URL_LENGTH;

  private static final String    WEBCAL_PREFIX  = "webcal://";

  private static final String    WEBCALS_PREFIX = "webcals://";

  private final SafeAddressGuard delegate;

  /**
   * The guard over a shared guard: the fetcher's, so that the rules applied
   * before a read are the ones its connections apply.
   *
   * @param delegate the platform's guard
   */
  public CalendarAddressGuard(SafeAddressGuard delegate) {
    this.delegate = delegate;
  }

  /**
   * Reads a URL as the user typed it: trimmed, {@code webcal://} and
   * {@code webcals://} read as {@code https://}, then normalized and checked by
   * the platform's guard. Nothing is resolved here.
   *
   * @param url the URL as typed
   * @return the URL to read
   * @throws CalendarFeedException with the reason it is refused
   */
  public URI normalize(String url) throws CalendarFeedException {
    return normalize(url, uri -> false);
  }

  /**
   * Reads a URL as the user typed it, as {@link #normalize(String)} does, but
   * keeps a URL whose only fault is an internal address literal when it is read
   * in the process, never over the network: a link of this eXo, whose public
   * domain may well be an internal address. Every other rule still applies,
   * since the platform's guard judges the literal last.
   *
   * @param url the URL as typed
   * @param readInProcess whether a URL is read without any connection
   * @return the URL to read
   * @throws CalendarFeedException with the reason it is refused
   */
  public URI normalize(String url, Predicate<URI> readInProcess) throws CalendarFeedException {
    String trimmed = StringUtils.trim(url);
    if (StringUtils.startsWithIgnoreCase(trimmed, WEBCALS_PREFIX)) {
      trimmed = "https://" + trimmed.substring(WEBCALS_PREFIX.length());
    } else if (StringUtils.startsWithIgnoreCase(trimmed, WEBCAL_PREFIX)) {
      trimmed = "https://" + trimmed.substring(WEBCAL_PREFIX.length());
    }
    try {
      return delegate.normalize(trimmed);
    } catch (SafeFetchException e) {
      if (e.getFailure() == SafeFetchFailure.REFUSED_ADDRESS) {
        URI inProcess = withoutFragment(trimmed);
        if (inProcess != null && readInProcess.test(inProcess)) {
          return inProcess;
        }
      }
      throw new CalendarFeedException(reason(e.getFailure()));
    }
  }

  /**
   * Checks the shape of a URL about to be read — the one typed, or the target
   * of a redirect.
   *
   * @param uri the URL
   * @return the URL with its scheme lower-cased
   * @throws CalendarFeedException with the reason it is refused
   */
  public URI checkTarget(URI uri) throws CalendarFeedException {
    try {
      return delegate.checkTarget(uri);
    } catch (SafeFetchException e) {
      throw new CalendarFeedException(reason(e.getFailure()));
    }
  }

  /**
   * A URL the platform's guard already found well formed, its fragment dropped
   * and its scheme lower-cased, as the guard returns the URLs it accepts.
   *
   * @param url the URL, trimmed
   * @return the URL, or null when it does not parse
   */
  private static URI withoutFragment(String url) {
    int fragment = url.indexOf('#');
    String target = fragment >= 0 ? url.substring(0, fragment) : url;
    int colon = target.indexOf(':');
    if (colon > 0) {
      target = target.substring(0, colon).toLowerCase(Locale.ROOT) + target.substring(colon);
    }
    try {
      return new URI(target);
    } catch (URISyntaxException e) {
      return null;
    }
  }

  /**
   * @return the platform's guard this one applies
   */
  public SafeAddressGuard getDelegate() {
    return delegate;
  }

  /**
   * The reason a calendar link cannot be read, from why the platform's fetcher
   * read nothing: the message code the user is shown.
   *
   * @param failure why nothing was read
   * @return the {@link CalendarFeedException} reason
   */
  public static String reason(SafeFetchFailure failure) {
    return switch (failure) {
    case INVALID_URL -> CalendarFeedException.INVALID_URL;
    case SCHEME_NOT_ALLOWED -> CalendarFeedException.SCHEME_NOT_ALLOWED;
    case PORT_NOT_ALLOWED -> CalendarFeedException.PORT_NOT_ALLOWED;
    case CREDENTIALS_IN_URL -> CalendarFeedException.CREDENTIALS_IN_URL;
    case REFUSED_ADDRESS -> CalendarFeedException.REFUSED_ADDRESS;
    case UNRESOLVABLE -> CalendarFeedException.UNRESOLVABLE;
    case TIMEOUT -> CalendarFeedException.TIMEOUT;
    case HTTP_ERROR -> CalendarFeedException.HTTP_ERROR;
    case TOO_LARGE -> CalendarFeedException.TOO_LARGE;
    case TOO_MANY_REDIRECTS -> CalendarFeedException.TOO_MANY_REDIRECTS;
    case CONTENT_TYPE_NOT_ALLOWED -> CalendarFeedException.NOT_A_CALENDAR;
    case UNREACHABLE -> CalendarFeedException.UNREACHABLE;
    };
  }

}
