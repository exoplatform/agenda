/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Test;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventRecurrence;

/**
 * Pins the rule that bounds a tokenised invitation link in time.
 *
 * <p>
 * A bare unit test on purpose: the bound is a pure function of the event, and
 * that is the property the calendar copy depends on (EXO-89753). A test needing
 * a container to establish it would not be establishing it.
 */
public class InvitationTokenExpiryTest {

  private static final int GRACE_HOURS = 24;

  /**
   * Builds a plain one-off event over a given period.
   *
   * @param start when the meeting starts
   * @param end when the meeting ends
   * @param allDay whether the event occupies whole days
   * @return the event, carrying nothing but its dates
   */
  private Event event(ZonedDateTime start, ZonedDateTime end, boolean allDay) {
    Event event = new Event();
    event.setStart(start);
    event.setEnd(end);
    event.setAllDay(allDay);
    event.setTimeZoneId(start.getZone());
    return event;
  }

  /**
   * A one-off meeting is answerable until its end, plus the grace period.
   */
  @Test
  public void aOneOffMeetingIsBoundedByItsOwnEnd() {
    ZonedDateTime start = ZonedDateTime.of(2026, 3, 4, 10, 0, 0, 0, ZoneId.of("Europe/Paris"));
    ZonedDateTime end = start.plusHours(1);

    assertEquals("the bound is the event's end plus the grace period",
                 end.plusHours(GRACE_HOURS).toEpochSecond(),
                 Utils.invitationTokenExpiry(event(start, end, false)));
  }

  /**
   * The end of a series is the end of the series, not the end of its first
   * meeting.
   *
   * <p>
   * This is the case a naive reading of {@link Event#getEnd()} gets wrong: for a
   * recurring event that field holds the first occurrence's end, so bounding by
   * it would retire the link after the first of fifty-two weekly meetings, while
   * an answer legitimately applies to all of them.
   */
  @Test
  public void aSeriesIsBoundedByItsLastOccurrenceNotItsFirst() {
    ZonedDateTime start = ZonedDateTime.of(2026, 3, 4, 10, 0, 0, 0, ZoneId.of("Europe/Paris"));
    ZonedDateTime firstOccurrenceEnd = start.plusHours(1);
    ZonedDateTime seriesEnd = start.plusYears(1);

    Event event = event(start, firstOccurrenceEnd, false);
    EventRecurrence recurrence = new EventRecurrence();
    recurrence.setOverallStart(start);
    recurrence.setOverallEnd(seriesEnd);
    event.setRecurrence(recurrence);

    long expiry = Utils.invitationTokenExpiry(event);

    assertEquals("a series is bounded by its overall end", seriesEnd.plusHours(GRACE_HOURS).toEpochSecond(), expiry);
    assertTrue("and therefore outlives the end of its first occurrence",
               expiry > firstOccurrenceEnd.plusHours(GRACE_HOURS).toEpochSecond());
  }

  /**
   * An endless series is bounded by the horizon the platform already stores for
   * it.
   *
   * <p>
   * {@code EntityMapper} writes {@code overallStart.plusYears(10)} as the end of
   * a recurrence that never ends, and reads it back into
   * {@code getOverallEnd()}. This test states that this method takes that answer
   * as given rather than inventing a second horizon of its own — an endless
   * standup gets ten years of answerable link, and nothing here has an opinion
   * about what "no end" means.
   */
  @Test
  public void anEndlessSeriesUsesTheHorizonThePlatformAlreadyStores() {
    ZonedDateTime start = ZonedDateTime.of(2026, 3, 4, 10, 0, 0, 0, ZoneId.of("Europe/Paris"));
    ZonedDateTime storedHorizon = start.plusYears(10);

    Event event = event(start, start.plusHours(1), false);
    EventRecurrence recurrence = new EventRecurrence();
    recurrence.setOverallStart(start);
    recurrence.setOverallEnd(storedHorizon);
    event.setRecurrence(recurrence);

    assertEquals("the stored ten year horizon is the bound, unmodified",
                 storedHorizon.plusHours(GRACE_HOURS).toEpochSecond(),
                 Utils.invitationTokenExpiry(event));
  }

  /**
   * An all-day event stays answerable for the whole of its last day.
   *
   * <p>
   * Its end is stored at the <i>start</i> of a day, so honouring that instant
   * literally would retire the link before the day it belongs to had begun.
   */
  @Test
  public void anAllDayEventIsBoundedByTheEndOfItsDay() {
    ZoneId paris = ZoneId.of("Europe/Paris");
    ZonedDateTime start = ZonedDateTime.of(2026, 3, 4, 0, 0, 0, 0, paris);
    ZonedDateTime endOfThatDay = start.plusDays(1).minusSeconds(1);

    long expiry = Utils.invitationTokenExpiry(event(start, start, true));

    assertEquals("an all day event lives to the last second of its day, then the grace period",
                 endOfThatDay.plusHours(GRACE_HOURS).toEpochSecond(),
                 expiry);
    assertTrue("which is strictly later than the midnight its end is stored at",
               expiry > start.plusHours(GRACE_HOURS).toEpochSecond());
  }

  /**
   * An event with no date at all yields no bound, and the caller must read that
   * as "cannot be answered" rather than as "never expires".
   *
   * <p>
   * The distinction is the whole defect: a missing bound that defaulted to
   * "unbounded" would reinstate the permanent link for exactly the events whose
   * data is malformed.
   */
  @Test
  public void anEventWithNoDatesYieldsNoBoundAtAll() {
    assertEquals("no event, no bound", 0, Utils.invitationTokenExpiry(null));
    assertEquals("an event with neither end nor start yields no bound", 0, Utils.invitationTokenExpiry(new Event()));
  }

  /**
   * The bound never reads the clock.
   *
   * <p>
   * <b>This is the test that protects the calendar copy.</b> The links go into
   * the DESCRIPTION of the pushed copy, and the mirror rewrites any copy whose
   * description changed. An expiry derived from "now" — a sliding window from
   * the moment of minting — would make every sweep produce a different token and
   * every copy churn for ever, which is the exact defect EXO-89716 was spent
   * removing. Computing the same event twice must give the same number.
   */
  @Test
  public void theBoundIsAPureFunctionOfTheEventAndNeverOfTheClock() throws InterruptedException {
    ZonedDateTime start = ZonedDateTime.of(2026, 3, 4, 10, 0, 0, 0, ZoneId.of("Europe/Paris"));
    Event event = event(start, start.plusHours(1), false);

    long first = Utils.invitationTokenExpiry(event);
    Thread.sleep(1100L);
    long second = Utils.invitationTokenExpiry(event);

    assertEquals("two computations a second apart must agree, or every calendar copy churns", first, second);
  }
}
