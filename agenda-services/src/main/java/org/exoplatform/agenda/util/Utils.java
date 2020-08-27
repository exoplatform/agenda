/*
 * Copyright (C) 2020 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.agenda.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;
import java.util.Date;
import java.util.TimeZone;

import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.model.*;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import jdk.internal.jline.internal.Log;
import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.RRule;

public class Utils {

  public static final String OCCURRENCE_ID_DATE_FORMAT = "yyyyMMdd'T'HHmmss'Z'";

  private Utils() {
  }

  public static void addUserSpacesIdentities(SpaceService spaceService,
                                             IdentityManager identityManager,
                                             String username,
                                             List<Long> identityIds) throws Exception {
    ListAccess<Space> userSpaces = spaceService.getMemberSpaces(username);
    int spacesSize = userSpaces.getSize();
    int offsetToFetch = 0;
    int limitToFetch = spacesSize > 20 ? 20 : spacesSize;
    while (limitToFetch > 0) {
      Space[] spaces = userSpaces.load(offsetToFetch, limitToFetch);
      Arrays.stream(spaces).forEach(space -> {
        Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName());
        identityIds.add(Long.parseLong(spaceIdentity.getId()));
      });
      offsetToFetch += limitToFetch;
      limitToFetch = (spacesSize - offsetToFetch) > 20 ? 20 : (spacesSize - offsetToFetch);
    }
  }

  public static List<Event> getOccurrences(Event event,
                                           LocalDate from,
                                           LocalDate to,
                                           TimeZone timeZone) {
    long startTime = event.getStart().toEpochSecond() * 1000;
    long endTime = event.getEnd().toEpochSecond() * 1000;

    DateTime startDateTime = new DateTime(startTime);
    DateTime endDateTime = new DateTime(endTime);
    VEvent vevent = new VEvent(startDateTime, endDateTime, event.getSummary());
    Recur recur = getICalendarRecur(event, event.getRecurrence(), timeZone);
    vevent.getProperties().add(new RRule(recur));

    long fromTime = from.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000;
    long toTime = to.atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC) * 1000;
    DateTime ical4jFrom = new DateTime(fromTime);
    DateTime ical4jTo = new DateTime(toTime);
    Period period = new Period(ical4jFrom, ical4jTo);
    PeriodList list = vevent.calculateRecurrenceSet(period);
    if (list == null || list.isEmpty()) {
      return Collections.emptyList();
    }

    List<Event> occurrences = new ArrayList<>();

    Iterator<?> periods = list.iterator();
    while (periods.hasNext()) {
      Period occurrencePeriod = (Period) periods.next();
      occurrencePeriod.getStart();
      occurrencePeriod.getEnd();
      Event occurrence = event.clone();
      occurrence.setStart(occurrencePeriod.getStart().toInstant().atZone(ZoneOffset.UTC));
      occurrence.setEnd(occurrencePeriod.getEnd().toInstant().atZone(ZoneOffset.UTC));
      String occurrenceId = buildOccurrenceId(occurrencePeriod.getStart(), timeZone);
      occurrence.setOccurrence(new EventOccurrence(occurrenceId, false));
      occurrence.setParentId(event.getId());
      occurrence.setRecurrence(null);
      occurrences.add(occurrence);
    }
    return occurrences;
  }

  @SuppressWarnings("unchecked")
  public static Recur getICalendarRecur(Event event, EventRecurrence recurrence, TimeZone timeZone) {
    try {
      EventRecurrenceFrequency frequency = recurrence.getFrequency();
      int count = recurrence.getCount();
      int interval = recurrence.getInterval();
      ZonedDateTime until = recurrence.getUntil();
      long untilTime = 0;
      if (until != null) {
        ZoneId zoneId = timeZone.toZoneId();
        untilTime = until.withZoneSameInstant(zoneId)
                         .toLocalDate()
                         .atTime(23, 59, 59)
                         .atZone(zoneId)
                         .toEpochSecond()
            * 1000;
      }

      Recur recur = null;
      switch (frequency) {
      case DAILY:
        if (untilTime > 0) {
          recur = new Recur(Recur.DAILY, new net.fortuna.ical4j.model.Date(untilTime));
        } else if (count > 0) {
          recur = new Recur(Recur.DAILY, count);
        } else {
          recur = new Recur("FREQ=DAILY");
        }
        recur.setInterval(interval);
        return recur;
      case WEEKLY:
        if (untilTime > 0) {
          recur = new Recur(Recur.WEEKLY, new net.fortuna.ical4j.model.Date(untilTime));
        } else if (count > 0) {
          recur = new Recur(Recur.WEEKLY, count);
        } else {
          recur = new Recur("FREQ=WEEKLY");
        }
        recur.setInterval(interval);
        List<String> repeatByDay = recurrence.getByDay();
        if (repeatByDay == null || repeatByDay.isEmpty()) {
          Log.warn("Event with id '" + event.getId() + "' has wrong 'repeatByDay' property value");
          return null;
        }
        WeekDayList weekDayList = new WeekDayList();
        for (String s : repeatByDay) {
          weekDayList.add(new WeekDay(s));
        }
        recur.getDayList().addAll(weekDayList);
        return recur;
      case MONTHLY:
        if (untilTime > 0) {
          recur = new Recur(Recur.MONTHLY, new net.fortuna.ical4j.model.Date(untilTime));
        } else if (count > 0) {
          recur = new Recur(Recur.MONTHLY, count);
        } else {
          recur = new Recur("FREQ=MONTHLY");
        }
        recur.setInterval(interval);

        List<String> repeatByMonthDay = recurrence.getByMonthDay();
        if (repeatByMonthDay != null && !repeatByMonthDay.isEmpty()) {
          NumberList numberList = new NumberList();
          for (String monthDay : repeatByMonthDay) {
            numberList.add(Integer.parseInt(monthDay));
          }
          recur.getMonthDayList().addAll(numberList);
        } else {
          repeatByDay = recurrence.getByDay();
          if (repeatByDay == null || repeatByDay.isEmpty()) {
            Log.warn("Event with id '" + event.getId() + "' has wrong 'repeatByDay' property value");
            return null;
          }
          weekDayList = new WeekDayList();
          for (String s : repeatByDay) {
            weekDayList.add(new WeekDay(s));
          }
          recur.getDayList().addAll(weekDayList);
        }
        return recur;
      case YEARLY:
        if (untilTime > 0) {
          recur = new Recur(Recur.YEARLY, new net.fortuna.ical4j.model.Date(untilTime));
        } else if (count > 0) {
          recur = new Recur(Recur.YEARLY, count);
        } else {
          recur = new Recur("FREQ=YEARLY");
        }
        recur.setInterval(interval);
        return recur;
      default:
        throw new UnsupportedOperationException("Frequency of type " + frequency.name() + " is not suported");
      }
    } catch (ParseException e) {
      throw new IllegalStateException("Error computing recurrence for event with id " + event.getId(), e);
    }
  }

  public static String buildOccurrenceId(Date formTime, TimeZone timeZone) {
    SimpleDateFormat format = new SimpleDateFormat(OCCURRENCE_ID_DATE_FORMAT);
    format.setTimeZone(timeZone);
    return format.format(formTime);
  }

}
