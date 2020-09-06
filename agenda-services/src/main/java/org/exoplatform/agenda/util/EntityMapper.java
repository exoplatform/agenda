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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.entity.*;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.model.Calendar;

import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.RRule;

public class EntityMapper {

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
                             eventReminderEntity.getReceiverId(),
                             eventReminderEntity.getBefore(),
                             eventReminderEntity.getBeforeType(),
                             AgendaDateUtils.fromDate(eventReminderEntity.getTriggerDate()));
  }

  public static EventReminderEntity toEntity(EventReminder eventReminder) {
    EventReminderEntity eventReminderEntity = new EventReminderEntity();
    eventReminderEntity.setId(eventReminder.getId());
    eventReminderEntity.setBefore(eventReminder.getBefore());
    eventReminderEntity.setBeforeType(eventReminder.getBeforePeriodType());
    eventReminderEntity.setReceiverId(eventReminder.getReceiverId());
    eventReminderEntity.setTriggerDate(AgendaDateUtils.toDate(eventReminder.getDatetime()));
    return eventReminderEntity;
  }

  public static Event fromEntity(EventEntity eventEntity) {
    long parentId = eventEntity.getParent() == null ? 0l : eventEntity.getParent().getId();
    long remoteProviderId = eventEntity.getRemoteProvider() == null ? 0l : eventEntity.getRemoteProvider().getId();

    ZonedDateTime startDate = AgendaDateUtils.fromDate(eventEntity.getStartDate());
    ZonedDateTime endDate = AgendaDateUtils.fromDate(eventEntity.getEndDate());

    EventOccurrence occurrence = null;
    EventRecurrenceEntity recurrenceEntity = eventEntity.getRecurrence();
    EventRecurrence recurrence = null;
    if (recurrenceEntity != null) {
      recurrence = fromEntity(recurrenceEntity, eventEntity);

      // Switch from DB dates stored in EventEntity and RecurrenceEntity for
      // recurrent events.
      // In fact, for recurrent events, overall recurrent event start and end
      // are stored in EventEntity to simplify querying on events for a period
      // of time, while the start and end date of each occurrence is stored in
      // RecurrenceEntity
      startDate = AgendaDateUtils.fromDate(recurrenceEntity.getStartDate());
      endDate = AgendaDateUtils.fromDate(recurrenceEntity.getEndDate());
    } else if (eventEntity.getOccurrenceId() != null) {
      occurrence = new EventOccurrence(AgendaDateUtils.fromDate(eventEntity.getOccurrenceId()), true);
    }

    return new Event(eventEntity.getId(),
                     parentId,
                     eventEntity.getRemoteId(),
                     remoteProviderId,
                     eventEntity.getCalendar().getId(),
                     eventEntity.getCreatorId(),
                     0l,
                     AgendaDateUtils.fromDate(eventEntity.getCreatedDate()),
                     AgendaDateUtils.fromDate(eventEntity.getUpdatedDate()),
                     eventEntity.getSummary(),
                     eventEntity.getDescription(),
                     eventEntity.getLocation(),
                     eventEntity.getColor(),
                     startDate,
                     endDate,
                     eventEntity.isAllDay(),
                     eventEntity.getAvailability(),
                     eventEntity.getStatus(),
                     recurrence,
                     occurrence,
                     null);
  }

  public static EventEntity toEntity(Event event) {
    EventEntity eventEntity = new EventEntity();
    eventEntity.setId(event.getId());
    eventEntity.setAllDay(event.isAllDay());
    eventEntity.setAvailability(event.getAvailability());
    eventEntity.setColor(event.getColor());
    eventEntity.setCreatedDate(AgendaDateUtils.toDate(event.getCreated()));
    eventEntity.setCreatorId(event.getCreatorId());
    eventEntity.setDescription(event.getDescription());
    eventEntity.setLocation(event.getLocation());
    eventEntity.setOccurrenceId(event.getOccurrence() == null ? null : AgendaDateUtils.toDate(event.getOccurrence().getId()));
    eventEntity.setRemoteId(event.getRemoteId());
    eventEntity.setStatus(event.getStatus());
    eventEntity.setSummary(event.getSummary());
    eventEntity.setUpdatedDate(AgendaDateUtils.toDate(event.getUpdated()));

    ZonedDateTime start = event.getStart();
    ZonedDateTime end = event.getEnd();

    EventRecurrence recurrence = event.getRecurrence();
    if (recurrence == null) {
      eventEntity.setStartDate(AgendaDateUtils.toDate(start));
      eventEntity.setEndDate(AgendaDateUtils.toDate(end));
    } else {
      long startTime = start.toEpochSecond() * 1000;
      long endTime = end.toEpochSecond() * 1000;

      DateTime startDateTime = new DateTime(startTime);
      DateTime endDateTime = new DateTime(endTime);

      VEvent vevent = new VEvent(startDateTime, endDateTime, event.getSummary());
      Recur recur = Utils.getICalendarRecur(event, recurrence);
      vevent.getProperties().add(new RRule(recur));

      long fromTime = start.toEpochSecond() * 1000;
      DateTime ical4jFrom = new DateTime(fromTime);

      ZonedDateTime untilDateTime = null;
      boolean neverEnds = true;
      if (recurrence.getUntil() != null) {
        neverEnds = false;
        untilDateTime = recurrence.getUntil().withHour(23).withMinute(59).withSecond(59);
      } else if (recurrence.getCount() > 0) {
        neverEnds = false;
        untilDateTime = end.withHour(23).withMinute(59).withSecond(59);
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
        untilDateTime = end.plusYears(1);
      }
      long toTime = untilDateTime.toEpochSecond() * 1000;
      DateTime ical4jTo = new DateTime(toTime);

      Period period = new Period(ical4jFrom, ical4jTo);
      PeriodList list = vevent.calculateRecurrenceSet(period);

      Period firstOccurrencePeriod = (Period) list.first();

      ZonedDateTime overallStart = firstOccurrencePeriod.getStart().toInstant().atZone(ZoneOffset.UTC);
      eventEntity.setStartDate(AgendaDateUtils.toDate(overallStart));

      if (!neverEnds) {
        Period lastOccurrencePeriod = (Period) list.last();
        ZonedDateTime overallEnd = lastOccurrencePeriod.getEnd().toInstant().atZone(ZoneOffset.UTC);
        eventEntity.setEndDate(AgendaDateUtils.toDate(overallEnd));
      }
    }
    return eventEntity;
  }

  public static EventRecurrence fromEntity(EventRecurrenceEntity recurrenceEntity, EventEntity eventEntity) {
    EventRecurrence recurrence;
    recurrence = new EventRecurrence();
    recurrence.setId(recurrenceEntity.getId());
    recurrence.setOverallStart(AgendaDateUtils.fromDate(eventEntity.getStartDate()));
    recurrence.setOverallEnd(AgendaDateUtils.fromDate(eventEntity.getEndDate()));
    Recur recur;
    try {
      recur = new Recur(recurrenceEntity.getRrule());
    } catch (ParseException e) {
      throw new IllegalStateException("Error parsing RRULE of recurrence of event " + eventEntity.getId(), e);
    }
    EventRecurrenceFrequency frequency = EventRecurrenceFrequency.valueOf(recur.getFrequency());
    recurrence.setFrequency(frequency);
    recurrence.setInterval(recur.getInterval());
    recurrence.setCount(recur.getCount());
    if (recur.getUntil() != null) {
      recurrence.setUntil(AgendaDateUtils.fromDate(recur.getUntil()));
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
    Recur recur = Utils.getICalendarRecur(event, recurrence);
    EventRecurrenceEntity eventRecurrenceEntity = new EventRecurrenceEntity();
    eventRecurrenceEntity.setRrule(recur.toString());
    // Store in Recurrence properties the start and end dates of the event as
    // given by the user
    eventRecurrenceEntity.setStartDate(AgendaDateUtils.toDate(event.getStart()));
    eventRecurrenceEntity.setEndDate(AgendaDateUtils.toDate(event.getEnd()));
    eventRecurrenceEntity.setId(recurrence.getId());
    return eventRecurrenceEntity;
  }

  public static EventAttendee fromEntity(EventAttendeeEntity eventAttendeeEntity) {
    return new EventAttendee(eventAttendeeEntity.getId(), eventAttendeeEntity.getIdentityId(), eventAttendeeEntity.getResponse());
  }

  public static EventAttendeeEntity toEntity(EventAttendee eventAttendee) {
    EventAttendeeEntity eventAttendeeEntity = new EventAttendeeEntity();
    eventAttendeeEntity.setId(eventAttendee.getId());
    eventAttendeeEntity.setIdentityId(eventAttendee.getIdentityId());
    eventAttendeeEntity.setResponse(eventAttendee.getResponse());
    return eventAttendeeEntity;
  }

  public static EventAttachment fromEntity(EventAttachmentEntity eventAttachmentEntity) {
    return new EventAttachment(eventAttachmentEntity.getId(),
                               eventAttachmentEntity.getFileId(),
                               eventAttachmentEntity.getEvent().getId());
  }

  public static EventConference fromEntity(EventConferenceEntity eventConferenceEntity) {
    return new EventConference(eventConferenceEntity.getId(),
                               eventConferenceEntity.getEvent().getId(),
                               eventConferenceEntity.getType(),
                               eventConferenceEntity.getUri(),
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
    eventConferenceEntity.setUri(eventConference.getUri());
    return eventConferenceEntity;
  }

  public static RemoteProvider fromEntity(RemoteProviderEntity remoteProviderEntity) {
    return new RemoteProvider(remoteProviderEntity.getId(), remoteProviderEntity.getName());
  }

  public static RemoteProviderEntity toEntity(RemoteProvider remoteProvider) {
    RemoteProviderEntity remoteProviderEntity = new RemoteProviderEntity();
    remoteProviderEntity.setId(remoteProvider.getId() <= 0 ? null : remoteProvider.getId());
    remoteProviderEntity.setName(remoteProvider.getName());
    return remoteProviderEntity;
  }

}
