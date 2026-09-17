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
package org.exoplatform.agenda.constant;

/**
 * Whether an event's <em>content</em> may be read outside eXo (EXO-90322).
 * <p>
 * <strong>This is not free/busy.</strong> {@link EventAvailability} says whether
 * an event takes the user's time; this says whether what the event <em>is</em>
 * may be shown. The two travel together in the form because they answer the two
 * halves of the same question, and they are honoured in two different places.
 * <p>
 * <strong>Where it is honoured: at every boundary a reader crosses only
 * because the calendar was shared.</strong> A {@link #PRIVATE} event is
 * published as a busy block — its time, and the word "Busy" — by
 * {@code CalendarFeedIcsWriter}, through
 * {@code AgendaCalendarLinkServiceImpl.isPrivate}; and since EXO-90357 it is
 * rendered the same way, masked, to a colleague who reads the calendar through
 * a share ({@link EventAccess#SHARED}). For everyone who could read the
 * calendar before sharing existed — its owner, the members of its space, the
 * invitees — it changes nothing.
 * <p>
 * The constants are stored by ordinal (the column is a {@code SMALLINT}, as
 * {@code AVAILABILITY} is), so <strong>their order is the storage format</strong>:
 * a new value goes at the end, and none is ever removed or reordered.
 */
public enum EventVisibility {

  /**
   * Inherit. There is nothing to inherit from yet — agenda models no
   * per-calendar visibility — so this resolves to "not masked", which is what
   * every event did before EXO-90322 and therefore what every existing row
   * means. It is ordinal 0 on purpose: the changeset backfills the column with
   * 0, so no stored event changes meaning.
   * <p>
   * Whether a per-calendar default is worth having is the PO's call; the day it
   * exists, this constant is where it is read.
   */
  DEFAULT,

  /**
   * The event's content may be published in full — the same treatment
   * {@link #DEFAULT} gets today, stated rather than inherited.
   */
  PUBLIC,

  /**
   * Only the fact that the time is taken may leave eXo. A published feed shows
   * a busy block with no title, description, location or attendee.
   */
  PRIVATE;
}
