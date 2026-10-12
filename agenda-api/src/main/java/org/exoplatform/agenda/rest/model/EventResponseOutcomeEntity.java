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
package org.exoplatform.agenda.rest.model;

import java.io.Serializable;

import org.exoplatform.agenda.constant.EventAttendeeResponse;

import lombok.*;

/**
 * What an answer stored, returned to the client that sent it.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventResponseOutcomeEntity implements Serializable {

  private static final long     serialVersionUID = -2894403720186712341L;

  /**
   * The answer stored at the date answered; when no date was answered, the
   * answer of the series' row valid at the date its answer is read at, which a
   * date with a row of its own does not decide.
   * {@link EventAttendeeResponse#WAITING} for a Yes given on a date with no
   * seat left.
   */
  private EventAttendeeResponse response;

  /**
   * Occurrence id, in RFC 3339, of the first date of a series the answer left
   * waiting; null when it left none.
   */
  private String                firstWaitingOccurrenceId;
}
