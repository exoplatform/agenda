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
package org.exoplatform.agenda.service;

/**
 * Thrown before any write when the rows a decide step locked are not what its
 * caller resolved: a row gone, or a date moved to another series. An answer
 * and an attendee list resolve again once. The other callers, and an answer
 * that misses again, report the event as not found; an attendee list that
 * misses again lets it go on as the illegal state it is.
 */
class ResolveAgainException extends IllegalStateException {

  private static final long serialVersionUID = 4383447437530349105L;

  ResolveAgainException(String message) {
    super(message);
  }
}
