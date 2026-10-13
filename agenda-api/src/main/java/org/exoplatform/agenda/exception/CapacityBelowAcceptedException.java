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
package org.exoplatform.agenda.exception;

import java.time.LocalDate;

/**
 * A capacity limit refused because it is below the Yes answers given at a
 * date the change reaches.
 * <p>
 * It is a subclass of {@link IllegalArgumentException} whose message is the
 * code {@value #CODE}, as for any other limit refused, so that a caller reading
 * only the code is unchanged. What the subclass adds is the date that blocks:
 * a limit of a series is checked against each of its dates still to come, and
 * the organiser who lowers it has to be told which one holds more Yes answers
 * than the limit asked (D16).
 */
public class CapacityBelowAcceptedException extends IllegalArgumentException {

  /** The code of a limit below the Yes answers, the exception's message. */
  public static final String CODE             = "agenda.capacity.belowAccepted";

  private static final long  serialVersionUID = 1L;

  private final LocalDate    date;

  /**
   * @param date the day, in the series' zone, of the first date of a series
   *          whose Yes answers the limit is below, or null when the limit is
   *          refused at the date or the event being saved
   */
  public CapacityBelowAcceptedException(LocalDate date) {
    super(CODE);
    this.date = date;
  }

  /**
   * @return the day, in the series' zone, of the first date of a series whose
   *         Yes answers the limit is below, or null when the limit is refused
   *         at the date or the event being saved
   */
  public LocalDate getDate() {
    return date;
  }
}
