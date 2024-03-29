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
import java.time.*;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.entity.*;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.RRule;

public class EntityMapper {

  private static final Log LOG = ExoLogger.getLogger(EntityMapper.class);

  private EntityMapper() {
  }

  public static Calendar fromEntity(CalendarEntity calendarEntity) {
    if (calendarEntity == null) {
      return null;
    }
    return new Calendar(calendarEntity.getId(),
                        calendarEntity.getOwnerId(),
                        calendarEntity.isSystem(),
                        null,
                        calendarEntity.getDescription(),
                        calendarEntity.getCreatedDate() == null ? null
                                                                : AgendaDateUtils.toRFC3339Date(calendarEntity.getCreatedDate()),
                        calendarEntity.getUpdatedDate() == null ? null
                                                                : AgendaDateUtils.toRFC3339Date(calendarEntity.getUpdatedDate()),
                        calendarEntity.getColor(),
                        null);
  }

  public static CalendarEntity toEntity(Calendar calendar) {
    if (calendar == null) {
      return null;
    }
    CalendarEntity calendarEntity = new CalendarEntity();
    if (calendar.getId() != 0) {
      calendarEntity.setId(calendar.getId());
    }
    if (calendar.getOwnerId() != 0) {
      calendarEntity.setOwnerId(calendar.getOwnerId());
    }
    calendarEntity.setColor(calendar.getColor());
    if (calendar.getCreated() != null) {
      calendarEntity.setCreatedDate(AgendaDateUtils.parseRFC3339Date(calendar.getCreated()));
    }
    if (calendar.getUpdated() != null) {
      calendarEntity.setUpdatedDate(AgendaDateUtils.parseRFC3339Date(calendar.getUpdated()));
    }
    calendarEntity.setDescription(calendar.getDescription());
    calendarEntity.setSystem(calendar.isSystem());
    return calendarEntity;
  }

  public static EventReminder fromEntity(EventReminderEntity eventReminderEntity) {
    return new EventReminder(eventReminderEntity.getId(),
                             eventReminderEntity.getEventId(),
                             eventReminderEntity.getReceiverId(),
                             eventReminderEntity.getBefore(),
                             eventReminderEntity.getBeforeType(),
                             AgendaDateUtils.fromDate(eventReminderEntity.getTriggerDate()),
                             AgendaDateUtils.fromDate(eventReminderEntity.getFromOccurrenceId()),
                             AgendaDateUtils.fromDate(eventReminderEntity.getUntilOccurrenceId()));
  }

  public static EventReminderEntity toEntity(EventReminder eventReminder) {
    EventReminderEntity eventReminderEntity = new EventReminderEntity();
    eventReminderEntity.setId(eventReminder.getId());
    eventReminderEntity.setBefore(eventReminder.getBefore());
    eventReminderEntity.setBeforeType(eventReminder.getBeforePeriodType());
    eventReminderEntity.setReceiverId(eventReminder.getReceiverId());
    eventReminderEntity.setEventId(eventReminder.getEventId());
    eventReminderEntity.setTriggerDate(AgendaDateUtils.toDate(eventReminder.getDatetime()));
    eventReminderEntity.setFromOccurrenceId(AgendaDateUtils.toDate(eventReminder.getFromOccurrenceId()));
    eventReminderEntity.setUntilOccurrenceId(AgendaDateUtils.toDate(eventReminder.getUntilOccurrenceId()));
    return eventReminderEntity;
  }

  public static Event fromEntity(EventEntity eventEntity) {
    long parentId = eventEntity.getParent() == null ? 0l : eventEntity.getParent().getId();

    ZonedDateTime startDate = null;
    ZonedDateTime endDate = null;

    EventOccurrence occurrence = null;
    EventRecurrenceEntity recurrenceEntity = eventEntity.getRecurrence();
    EventRecurrence recurrence = null;
    if (recurrenceEntity == null) {
      startDate = AgendaDateUtils.fromDate(eventEntity.getStartDate());
      endDate = AgendaDateUtils.fromDate(eventEntity.getEndDate());
      if (eventEntity.getOccurrenceId() != null) {
        occurrence = new EventOccurrence(AgendaDateUtils.fromDate(eventEntity.getOccurrenceId()),
                                         true,
                                         eventEntity.isOccurrencePeriodChanged());
      }
    } else {
      recurrence = fromEntity(recurrenceEntity, eventEntity);

      // Switch from DB dates stored in EventEntity and RecurrenceEntity for
      // recurrent events.
      // In fact, for recurrent events, overall recurrent event start and end
      // are stored in EventEntity to simplify querying on events for a period
      // of time, while the start and end date of each occurrence is stored in
      // RecurrenceEntity
      startDate = AgendaDateUtils.fromDate(recurrenceEntity.getStartDate());
      endDate = AgendaDateUtils.fromDate(recurrenceEntity.getEndDate());
    }

    ZoneId eventZoneId = eventEntity.getTimeZoneId() == null ? ZoneOffset.UTC : ZoneId.of(eventEntity.getTimeZoneId());

    ZonedDateTime createdDate = AgendaDateUtils.fromDate(eventEntity.getCreatedDate());
    ZonedDateTime updatedDate = null;
    if (eventEntity.getModifierId() > 0) {
      // Set updatedDate = null when no update was made on Event
      updatedDate = AgendaDateUtils.fromDate(eventEntity.getUpdatedDate());
    }
    return new Event(eventEntity.getId(),
                     parentId,
                     eventEntity.getCalendar().getId(),
                     eventEntity.getCreatorId(),
                     eventEntity.getModifierId(),
                     createdDate,
                     updatedDate,
                     eventEntity.getSummary(),
                     eventEntity.getDescription(),
                     eventEntity.getLocation(),
                     eventEntity.getColor(),
                     eventZoneId,
                     startDate,
                     endDate,
                     eventEntity.isAllDay(),
                     eventEntity.getAvailability(),
                     eventEntity.getStatus(),
                     recurrence,
                     occurrence,
                     null,
                     eventEntity.isAllowAttendeeToUpdate(),
                     eventEntity.isAllowAttendeeToInvite());
  }

  public static EventEntity toEntity(Event event) {
    ZoneId eventZoneId = event.getTimeZoneId() == null ? ZoneOffset.UTC : event.getTimeZoneId();
    // iCal4j doesn't recognize ZoneOffset.UTC, thus we use here
    // ZoneId.of("UTC")
    ZoneId iCal4jZoneId = event.getTimeZoneId() == null ? ZoneId.of("UTC") : event.getTimeZoneId();
    TimeZone ical4jTimezone = Utils.getICalTimeZone(iCal4jZoneId);

    EventEntity eventEntity = new EventEntity();
    eventEntity.setId(event.getId());
    eventEntity.setAllDay(event.isAllDay());
    eventEntity.setAvailability(event.getAvailability());
    eventEntity.setColor(event.getColor());
    eventEntity.setCreatorId(event.getCreatorId());
    eventEntity.setModifierId(event.getModifierId());
    eventEntity.setDescription(event.getDescription());
    eventEntity.setLocation(event.getLocation());
    eventEntity.setOccurrenceId(event.getOccurrence() == null ? null : AgendaDateUtils.toDate(event.getOccurrence().getId()));
    eventEntity.setOccurrencePeriodChanged(event.getOccurrence() != null && event.getOccurrence().isDatesModified());
    eventEntity.setStatus(event.getStatus());
    eventEntity.setSummary(event.getSummary());
    // Add createdDate = updatedDate to be able to sort on it in DB queries
    Date createdDate = AgendaDateUtils.toDate(event.getCreated());
    eventEntity.setCreatedDate(createdDate);
    if (event.getModifierId() <= 0) {
      eventEntity.setUpdatedDate(createdDate);
    } else {
      eventEntity.setUpdatedDate(AgendaDateUtils.toDate(event.getUpdated()));
    }
    eventEntity.setAllowAttendeeToInvite(event.isAllowAttendeeToInvite());
    eventEntity.setAllowAttendeeToUpdate(event.isAllowAttendeeToUpdate());
    eventEntity.setTimeZoneId(eventZoneId.getId());

    ZonedDateTime start = event.getStart();
    ZonedDateTime end = event.getEnd();

    ZonedDateTime startUTC = null;
    ZonedDateTime endUTC = null;
    if (event.isAllDay()) {
      start = start.toLocalDate()
                   .atStartOfDay(eventZoneId);
      end = end.toLocalDate()
               .atStartOfDay(eventZoneId)
               .plusDays(1)
               .minusSeconds(1);

      startUTC = start.toLocalDate()
                      .atStartOfDay(ZoneOffset.UTC);
      endUTC = end.toLocalDate()
                  .atStartOfDay(ZoneOffset.UTC)
                  .plusDays(1)
                  .minusSeconds(1);
    } else {
      start = start.withZoneSameInstant(eventZoneId);
      end = end.withZoneSameInstant(eventZoneId);

      startUTC = start.withZoneSameInstant(ZoneOffset.UTC);
      endUTC = end.withZoneSameInstant(ZoneOffset.UTC);
    }

    EventRecurrence recurrence = event.getRecurrence();
    if (event.getOccurrence() != null && recurrence != null) {
      LOG.warn("Occurrence with id " + event.getOccurrence().getId() + " shouldn't have a recurrence");
      recurrence = null;
    }

    if (recurrence == null) {
      eventEntity.setStartDate(AgendaDateUtils.toDate(startUTC));
      eventEntity.setEndDate(AgendaDateUtils.toDate(endUTC));
    } else if (event.getStatus() != EventStatus.CANCELLED) {
      DateTime startDateTime = new DateTime(Date.from(start.toInstant()), ical4jTimezone);
      DateTime endDateTime = new DateTime(Date.from(end.toInstant()), ical4jTimezone);

      VEvent vevent = new VEvent(startDateTime, endDateTime, event.getSummary());
      Recur recur = Utils.getICalendarRecur(recurrence, eventZoneId);
      vevent.getProperties().add(new RRule(recur));

      DateTime ical4jFrom = new DateTime(Date.from(start.toInstant()), ical4jTimezone);

      ZonedDateTime untilDateTime = null;
      boolean neverEnds = true;
      if (recurrence.getUntil() != null) {
        neverEnds = false;
        untilDateTime = recurrence.getUntil().atStartOfDay(eventZoneId).withHour(23).withMinute(59).withSecond(59);
      } else if (recurrence.getCount() > 0) {
        neverEnds = false;
        if (event.isAllDay()) {
          untilDateTime = end.toLocalDate().atStartOfDay(eventZoneId).withHour(23).withMinute(59).withSecond(59);
        } else {
          untilDateTime = end.withZoneSameInstant(eventZoneId).plusSeconds(1);
        }
        int countIntervals = recurrence.getInterval() * recurrence.getCount();
        switch (recurrence.getFrequency()) {
          case YEARLY:
            untilDateTime = untilDateTime.plusYears(countIntervals);
            break;
          case MONTHLY:
            untilDateTime = untilDateTime.plusMonths(countIntervals);
            break;
          case WEEKLY:
            untilDateTime = untilDateTime.plusWeeks(countIntervals);
            break;
          case DAILY:
            untilDateTime = untilDateTime.plusDays(countIntervals);
            break;
          default:
            break;
        }
      } else {
        neverEnds = true;
        untilDateTime = end.plusMonths(2);
      }
      DateTime ical4jTo = new DateTime(Date.from(untilDateTime.toInstant()), ical4jTimezone);

      Period period = new Period(ical4jFrom, ical4jTo);
      period.setTimeZone(ical4jTimezone);
      PeriodList list = vevent.calculateRecurrenceSet(period);

      Period firstOccurrencePeriod =
                                   list.isEmpty() ? null
                                                  : list.stream()
                                                        .min((period1,
                                                              period2) -> period1.getStart()
                                                                                 .compareTo(period2.getStart()))
                                                        .orElse(null);

      DateTime firstOccurrenceOverallStart = firstOccurrencePeriod == null ? startDateTime
                                                                           : firstOccurrencePeriod.getStart();
      Period lastOccurrencePeriod = null;
      if (!neverEnds && !list.isEmpty()) {
        lastOccurrencePeriod = list.stream()
                                   .max((period1,
                                         period2) -> period1.getStart()
                                                            .compareTo(period2.getStart()))
                                   .orElse(null);
      }
      ZonedDateTime overallStart = null;
      ZonedDateTime overallEnd = null;
      if (event.isAllDay()) {
        overallStart = firstOccurrenceOverallStart.toInstant().atZone(eventZoneId).toLocalDate().atStartOfDay(eventZoneId);

        if (lastOccurrencePeriod != null) {
          overallEnd = lastOccurrencePeriod.getEnd()
                                           .toInstant()
                                           .atZone(eventZoneId)
                                           .toLocalDate()
                                           .plusDays(1)
                                           .atStartOfDay(eventZoneId)
                                           .minusSeconds(1);
        }
      } else {
        overallStart = firstOccurrenceOverallStart.toInstant().atZone(eventZoneId);

        if (lastOccurrencePeriod != null) {
          overallEnd = lastOccurrencePeriod.getEnd().toInstant().atZone(eventZoneId);
        }
      }

      // Give a far date as default value for overall end when the recurrent
      // event doesn't end
      if (overallEnd == null) {
        overallEnd = overallStart.plusYears(10);
      }

      if (event.isAllDay()) {
        overallStart = overallStart.toLocalDate().atStartOfDay(ZoneOffset.UTC);
        overallEnd = overallEnd.toLocalDate().atStartOfDay(ZoneOffset.UTC).plusDays(1).minusSeconds(1);
      } else {
        overallStart = overallStart.withZoneSameInstant(ZoneOffset.UTC);
        overallEnd = overallEnd.withZoneSameInstant(ZoneOffset.UTC);
      }

      eventEntity.setStartDate(AgendaDateUtils.toDate(overallStart));
      eventEntity.setEndDate(AgendaDateUtils.toDate(overallEnd));
    }
    return eventEntity;
  }

  public static EventRecurrence fromEntity(EventRecurrenceEntity recurrenceEntity, EventEntity eventEntity) {
    EventRecurrence recurrence = new EventRecurrence();
    recurrence.setId(recurrenceEntity.getId());
    recurrence.setType(recurrenceEntity.getType());

    ZoneId eventTimeZoneId = null;
    if (eventEntity.getTimeZoneId() != null) {
      eventTimeZoneId = ZoneId.of(eventEntity.getTimeZoneId());
    }

    ZonedDateTime overallStart = AgendaDateUtils.fromDate(eventEntity.getStartDate());
    ZonedDateTime overallEnd = AgendaDateUtils.fromDate(eventEntity.getEndDate());
    if (eventTimeZoneId != null) {
      if (eventEntity.isAllDay()) {
        if (overallStart != null) {
          overallStart = overallStart.toLocalDate().atStartOfDay(eventTimeZoneId);
        }
        if (overallEnd != null) {
          overallEnd = overallEnd.toLocalDate().plusDays(1).atStartOfDay(eventTimeZoneId).minusSeconds(1);
        }
      } else {
        if (overallStart != null) {
          overallStart = overallStart.withZoneSameInstant(eventTimeZoneId);
        }
        if (overallEnd != null) {
          overallEnd = overallEnd.withZoneSameInstant(eventTimeZoneId);
        }
      }
    }

    recurrence.setOverallStart(overallStart);
    recurrence.setOverallEnd(overallEnd);
    recurrence.setRrule(recurrenceEntity.getRrule());

    Recur recur;
    try {
      recur = new Recur(recurrence.getRrule());
    } catch (ParseException e) {
      throw new IllegalStateException("Error parsing RRULE of recurrence of event " + eventEntity.getId(), e);
    }
    EventRecurrenceFrequency frequency = EventRecurrenceFrequency.valueOf(recur.getFrequency().name());
    recurrence.setFrequency(frequency);
    recurrence.setInterval(recur.getInterval());
    recurrence.setCount(recur.getCount() > 0 ? recur.getCount() : 0);
    if (recur.getUntil() != null) {
      LocalDate untilDate = recur.getUntil()
                                 .toInstant()
                                 .atZone(ZoneId.systemDefault())
                                 .toLocalDate();
      recurrence.setUntil(untilDate);
    }

    if (recur.getSecondList() != null && !recur.getSecondList().isEmpty()) {
      @SuppressWarnings("all")
      List<String> seconds = (List<String>) recur.getSecondList().stream().map(Object::toString).collect(Collectors.toList());
      recurrence.setBySecond(seconds);
    }
    if (recur.getMinuteList() != null && !recur.getMinuteList().isEmpty()) {
      @SuppressWarnings("all")
      List<String> minutes = (List<String>) recur.getMinuteList().stream().map(Object::toString).collect(Collectors.toList());
      recurrence.setByMinute(minutes);
    }
    if (recur.getHourList() != null && !recur.getHourList().isEmpty()) {
      @SuppressWarnings("all")
      List<String> hours = (List<String>) recur.getHourList().stream().map(Object::toString).collect(Collectors.toList());
      recurrence.setByHour(hours);
    }
    if (recur.getDayList() != null && !recur.getDayList().isEmpty()) {
      @SuppressWarnings("all")
      List<String> days = (List<String>) recur.getDayList().stream().map(Object::toString).collect(Collectors.toList());
      recurrence.setByDay(days);
    }
    if (recur.getMonthDayList() != null && !recur.getMonthDayList().isEmpty()) {
      @SuppressWarnings("all")
      List<String> monthDays = (List<String>) recur.getMonthDayList().stream().map(Object::toString).collect(Collectors.toList());
      recurrence.setByMonthDay(monthDays);
    }
    if (recur.getYearDayList() != null && !recur.getYearDayList().isEmpty()) {
      @SuppressWarnings("all")
      List<String> yearDays = (List<String>) recur.getYearDayList().stream().map(Object::toString).collect(Collectors.toList());
      recurrence.setByYearDay(yearDays);
    }
    if (recur.getWeekNoList() != null && !recur.getWeekNoList().isEmpty()) {
      @SuppressWarnings("all")
      List<String> weekNos = (List<String>) recur.getWeekNoList().stream().map(Object::toString).collect(Collectors.toList());
      recurrence.setByWeekNo(weekNos);
    }
    if (recur.getMonthList() != null && !recur.getMonthList().isEmpty()) {
      @SuppressWarnings("all")
      List<String> months = (List<String>) recur.getMonthList().stream().map(Object::toString).collect(Collectors.toList());
      recurrence.setByMonth(months);
    }
    if (recur.getSetPosList() != null && !recur.getSetPosList().isEmpty()) {
      @SuppressWarnings("all")
      List<String> setPos = (List<String>) recur.getSetPosList().stream().map(Object::toString).collect(Collectors.toList());
      recurrence.setBySetPos(setPos);
    }
    return recurrence;
  }

  public static EventRecurrenceEntity toEntity(Event event, EventRecurrence recurrence) {
    Recur recur = Utils.getICalendarRecur(recurrence, event.getTimeZoneId());
    EventRecurrenceEntity eventRecurrenceEntity = new EventRecurrenceEntity();
    eventRecurrenceEntity.setType(recurrence.getType());
    eventRecurrenceEntity.setRrule(recur.toString());
    // Store in Recurrence properties the start and end dates of the event as
    // given by the user
    eventRecurrenceEntity.setStartDate(AgendaDateUtils.toDate(event.getStart()));
    eventRecurrenceEntity.setEndDate(AgendaDateUtils.toDate(event.getEnd()));
    eventRecurrenceEntity.setId(recurrence.getId());
    return eventRecurrenceEntity;
  }

  public static EventAttendee fromEntity(EventAttendeeEntity eventAttendeeEntity, long eventId) {
    return new EventAttendee(eventAttendeeEntity.getId(),
                             eventId,
                             eventAttendeeEntity.getIdentityId(),
                             AgendaDateUtils.fromDate(eventAttendeeEntity.getFromOccurrenceId()),
                             AgendaDateUtils.fromDate(eventAttendeeEntity.getUntilOccurrenceId()),
                             eventAttendeeEntity.getResponse());
  }

  public static EventAttendeeEntity toEntity(EventAttendee eventAttendee) {
    EventAttendeeEntity eventAttendeeEntity = new EventAttendeeEntity();
    eventAttendeeEntity.setId(eventAttendee.getId());
    eventAttendeeEntity.setIdentityId(eventAttendee.getIdentityId());
    eventAttendeeEntity.setResponse(eventAttendee.getResponse());
    eventAttendeeEntity.setFromOccurrenceId(AgendaDateUtils.toDate(eventAttendee.getFromOccurrenceId()));
    eventAttendeeEntity.setUntilOccurrenceId(AgendaDateUtils.toDate(eventAttendee.getUntilOccurrenceId()));
    return eventAttendeeEntity;
  }

  public static EventConference fromEntity(EventConferenceEntity eventConferenceEntity) {
    return new EventConference(eventConferenceEntity.getId(),
                               eventConferenceEntity.getEvent().getId(),
                               eventConferenceEntity.getType(),
                               eventConferenceEntity.getUrl(),
                               eventConferenceEntity.getPhone(),
                               eventConferenceEntity.getAccessCode(),
                               eventConferenceEntity.getDescription());
  }

  public static EventConferenceEntity toEntity(EventConference eventConference) {
    EventConferenceEntity eventConferenceEntity = new EventConferenceEntity();
    eventConferenceEntity.setId(eventConference.getId());
    eventConferenceEntity.setAccessCode(eventConference.getAccessCode());
    eventConferenceEntity.setDescription(eventConference.getDescription());
    eventConferenceEntity.setPhone(eventConference.getPhone());
    eventConferenceEntity.setType(eventConference.getType());
    eventConferenceEntity.setUrl(eventConference.getUrl());
    return eventConferenceEntity;
  }

  public static RemoteProvider fromEntity(RemoteProviderEntity remoteProviderEntity) {
    return new RemoteProvider(remoteProviderEntity.getId(),
                              remoteProviderEntity.getName(),
                              remoteProviderEntity.getApiKey(),
                              remoteProviderEntity.getSecretKey(),
                              remoteProviderEntity.isEnabled(),
                        null);
  }

  public static RemoteProviderEntity toEntity(RemoteProvider remoteProvider) {
    RemoteProviderEntity remoteProviderEntity = new RemoteProviderEntity();
    remoteProviderEntity.setId(remoteProvider.getId() <= 0 ? null : remoteProvider.getId());
    remoteProviderEntity.setName(remoteProvider.getName());
    remoteProviderEntity.setApiKey(remoteProvider.getApiKey());
    remoteProviderEntity.setSecretKey(remoteProvider.getSecretKey());
    remoteProviderEntity.setEnabled(remoteProvider.isEnabled());
    return remoteProviderEntity;
  }

  public static RemoteEventEntity toEntity(RemoteEvent remoteEvent) {
    RemoteEventEntity remoteEventEntity = new RemoteEventEntity();
    remoteEventEntity.setId(remoteEvent.getId() == 0 ? null : remoteEvent.getId());
    remoteEventEntity.setIdentityId(remoteEvent.getIdentityId());
    remoteEventEntity.setEventId(remoteEvent.getEventId());
    remoteEventEntity.setRemoteId(remoteEvent.getRemoteId());
    remoteEventEntity.setRemoteProviderId(remoteEvent.getRemoteProviderId());
    return remoteEventEntity;
  }

  public static RemoteEvent fromEntity(RemoteEventEntity remoteEventEntity, RemoteProviderEntity remoteProviderEntity) {
    if (remoteEventEntity == null) {
      return null;
    }
    return new RemoteEvent(remoteEventEntity.getId(),
                           remoteEventEntity.getEventId(),
                           remoteEventEntity.getIdentityId(),
                           remoteEventEntity.getRemoteId(),
                           remoteEventEntity.getRemoteProviderId(),
                           remoteProviderEntity == null ? null : remoteProviderEntity.getName());
  }

}
