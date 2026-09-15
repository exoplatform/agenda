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

/**
 * Why a calendar link cannot be read (EXO-90278), as the message code the user
 * is shown: {@code agenda.calendarSubscription.<reason>}. The message never
 * carries the URL, which may embed a secret.
 */
public class CalendarFeedException extends Exception {

  /** Prefix of every message code. */
  public static final String CODE_PREFIX         = "agenda.calendarSubscription.";

  /** Not a usable absolute URL. */
  public static final String INVALID_URL         = "invalidUrl";

  /** A scheme other than http, https or webcal. */
  public static final String SCHEME_NOT_ALLOWED  = "schemeNotAllowed";

  /** A port outside the allowed set. */
  public static final String PORT_NOT_ALLOWED    = "portNotAllowed";

  /** A user name or password in the URL. */
  public static final String CREDENTIALS_IN_URL  = "credentialsInUrl";

  /** The host resolves to an address the platform must not reach. */
  public static final String REFUSED_ADDRESS     = "refusedAddress";

  /** The host resolves to nothing. */
  public static final String UNRESOLVABLE        = "unresolvable";

  /** No answer could be read. */
  public static final String UNREACHABLE         = "unreachable";

  /** The answer took too long. */
  public static final String TIMEOUT             = "timeout";

  /** The server answered with an error status. */
  public static final String HTTP_ERROR          = "httpError";

  /** The answer is larger than allowed. */
  public static final String TOO_LARGE           = "tooLarge";

  /** Too many redirects. */
  public static final String TOO_MANY_REDIRECTS  = "tooManyRedirects";

  /** The answer is not an iCalendar document. */
  public static final String NOT_A_CALENDAR      = "notACalendar";

  /** The answer looks like an iCalendar document but cannot be read. */
  public static final String MALFORMED_CALENDAR  = "malformedCalendar";

  /** A link of this eXo that opens nothing, or no longer does. */
  public static final String LINK_NOT_FOUND      = "linkNotFound";

  /** A link of this eXo to the user's own calendar. */
  public static final String OWN_CALENDAR        = "ownCalendar";

  /**
   * A link of this eXo to a calendar the user already sees in their agenda
   * through a space.
   */
  public static final String ALREADY_IN_AGENDA   = "alreadyInAgenda";

  private static final long  serialVersionUID    = -2261016484066153471L;

  private final String       reason;

  /**
   * Builds the exception for a reason.
   *
   * @param reason one of the reason constants
   */
  public CalendarFeedException(String reason) {
    super(CODE_PREFIX + reason);
    this.reason = reason;
  }

  /**
   * Builds the exception for a reason, keeping its cause for the logs.
   *
   * @param reason one of the reason constants
   * @param cause what failed
   */
  public CalendarFeedException(String reason, Throwable cause) {
    super(CODE_PREFIX + reason, cause);
    this.reason = reason;
  }

  /**
   * @return the reason, one of the constants
   */
  public String getReason() {
    return reason;
  }

  /**
   * @return the full message code, {@code agenda.calendarSubscription.<reason>}
   */
  public String getCode() {
    return getMessage();
  }

}
