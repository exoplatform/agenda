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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.*;
import java.util.*;
import java.util.Date;
import java.util.stream.Collectors;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Month;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.property.*;
import net.fortuna.ical4j.util.RandomUidGenerator;
import net.fortuna.ical4j.util.UidGenerator;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.*;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.HTMLEntityEncoder;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.portal.branding.BrandingService;
import org.exoplatform.portal.localization.LocaleContextInfoUtils;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.resources.LocaleContextInfo;
import org.exoplatform.services.resources.LocalePolicy;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Recur.Frequency;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.component.VEvent;
import org.exoplatform.social.metadata.model.MetadataKey;
import org.exoplatform.social.metadata.model.MetadataType;

public class Utils {

  private static final Log              LOG                            = ExoLogger.getLogger(Utils.class);

  private static class ICal4jTimeZoneRegistryHolder {
    private static final TimeZoneRegistry INSTANCE = TimeZoneRegistryFactory.getInstance().createRegistry();
  }

  public static final String EVENT_METADATA_NAME                       = "agendaEvent";

  public static final MetadataType EVENT_METADATA_TYPE                 = new MetadataType(1100, EVENT_METADATA_NAME);

  public static final MetadataKey EVENT_METADATA_KEY                   = new MetadataKey(EVENT_METADATA_TYPE.getName(), EVENT_METADATA_NAME, 0);

  public static final String EVENT_ID                                  = "eventId";

  public static final String CONTENT_ID                                = "contentId";

  public static final String            POST_CREATE_AGENDA_EVENT_EVENT = "exo.agenda.event.created";

  public static final String            POST_UPDATE_AGENDA_EVENT_EVENT = "exo.agenda.event.updated";

  public static final String            POST_DELETE_AGENDA_EVENT_EVENT = "exo.agenda.event.deleted";

  public static final String            POST_MOVE_AGENDA_EVENT_EVENT   = "exo.agenda.event.moved";

  public static final String            POST_EVENT_RESPONSE_SENT       = "exo.agenda.event.responseSent";

  public static final String            POST_EVENT_RESPONSE_SAVED      = "exo.agenda.event.responseSaved";

  public static final String            POST_CREATE_AGENDA_EVENT_POLL  = "exo.agenda.event.poll.created";

  public static final String            POST_VOTES_AGENDA_EVENT_POLL   = "exo.agenda.event.poll.voted.all";

  public static final String            POST_VOTE_AGENDA_EVENT_POLL    = "exo.agenda.event.poll.voted";

  public static final String            POST_DISMISS_AGENDA_EVENT_POLL = "exo.agenda.event.poll.dismissed";

  /**
   * Stands in for a line break while an HTML fragment is being flattened to
   * text: a control character no editor content can carry, so it survives the
   * markup being stripped and is swapped back for a real newline afterwards.
   */
  private static final String           LINE_BREAK_MARKER              = "\u0001";

  private Utils() {
  }

  public static List<Long> getCalendarOwnersOfUser(SpaceService spaceService,
                                                   IdentityManager identityManager,
                                                   Identity userIdentity) {
    List<Long> calendarOwners = new ArrayList<>();
    String userIdentityId = userIdentity.getId();
    calendarOwners.add(Long.parseLong(userIdentityId));
    try {
      Utils.addUserSpacesIdentities(spaceService, identityManager, userIdentity.getRemoteId(), calendarOwners);
    } catch (Exception e) {
      throw new IllegalStateException("Error while retrieving spaces of user with id: " + userIdentityId, e);
    }
    return calendarOwners;
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

  public static List<Event> getOccurrences(Event event, LocalDate from, LocalDate to, int limit) {
    ZoneId timeZone = event.getTimeZoneId();

    TimeZone ical4jTimezone = getICalTimeZone(timeZone);
    boolean allDay = event.isAllDay();
    ZonedDateTime startTime = allDay ? event.getStart().toLocalDate().atStartOfDay(timeZone)
                                     : event.getStart();
    ZonedDateTime endTime = allDay ? event.getEnd()
                                          .toLocalDate()
                                          .atStartOfDay(timeZone)
                                          .plusDays(1)
                                          .minusSeconds(1)
                                   : event.getEnd();

    DateTime startDateTime = new DateTime(Date.from(startTime.toInstant()));
    startDateTime.setTimeZone(ical4jTimezone);
    DateTime endDateTime = new DateTime(Date.from(endTime.toInstant()));
    endDateTime.setTimeZone(ical4jTimezone);

    VEvent vevent = new VEvent(startDateTime, endDateTime, event.getSummary());
    EventRecurrence recurrence = event.getRecurrence();
    Recur recur = getICalendarRecur(recurrence, timeZone);
    vevent.getProperties().add(new RRule(recur));

    ZonedDateTime fromTime = from.atStartOfDay(timeZone);
    if (to == null) {
      ZonedDateTime overallEnd = recurrence.getOverallEnd();
      if (overallEnd == null) {
        to = from.plusYears(5);
      } else {
        to = overallEnd.withZoneSameInstant(ZoneOffset.UTC).toLocalDate();
      }
    }
    ZonedDateTime toTime = to.atStartOfDay(timeZone).plusDays(1).minusSeconds(1);
    DateTime ical4jFrom = new DateTime(Date.from(fromTime.toInstant()));
    ical4jFrom.setTimeZone(ical4jTimezone);
    DateTime ical4jTo = new DateTime(Date.from(toTime.toInstant()));
    ical4jTo.setTimeZone(ical4jTimezone);
    DateList dates = limit > 0 ? recur.getDates(startDateTime, ical4jFrom, ical4jTo, null, limit)
                               : recur.getDates(startDateTime, ical4jFrom, ical4jTo, null);
    if (dates == null || dates.isEmpty()) {
      return Collections.emptyList();
    }
    @SuppressWarnings("all")
    List<LocalDate> occurrencesIds = (List<LocalDate>) dates.stream()
                                                            .map(date -> getOccurrenceId(allDay,
                                                                                         ((DateTime) date),
                                                                                         timeZone).toLocalDate())
                                                            .collect(Collectors.toList());

    if (limit > 0 && dates.size() >= limit) {
      // Limit period of dates to retrieve of this recurrence to date where we
      // have at maximum 'limit' occurrences that will be retrieved
      ical4jTo = (DateTime) dates.get(limit - 1);
      long duration = (endTime.toEpochSecond() - startTime.toEpochSecond()) * 1000;

      ical4jTo = new DateTime(ical4jTo.getTime() + duration + 1000);
      ical4jTo.setTimeZone(ical4jTimezone);
    }
    Period period = new Period(ical4jFrom, ical4jTo);
    period.setTimeZone(ical4jTimezone);
    PeriodList list = vevent.calculateRecurrenceSet(period);

    List<Event> occurrences = new ArrayList<>();

    Iterator<?> periods = list.iterator();
    while (periods.hasNext()) {
      Period occurrencePeriod = (Period) periods.next();
      ZonedDateTime occurrenceId = null;
      DateTime eventStartDate = occurrencePeriod.getStart();
      occurrenceId = getOccurrenceId(allDay, eventStartDate, timeZone);
      if (!occurrencesIds.contains(occurrenceId.toLocalDate())) {
        continue;
      }
      Event occurrence = event.clone();
      occurrence.setId(0);
      occurrence.setStart(eventStartDate.toInstant().atZone(timeZone));
      occurrence.setEnd(occurrencePeriod.getEnd().toInstant().atZone(timeZone));
      occurrence.setOccurrence(new EventOccurrence(occurrenceId, false, false));
      occurrence.setParentId(event.getId());
      occurrence.setRecurrence(null);
      occurrences.add(occurrence);
    }
    return occurrences;
  }

  public static ZonedDateTime getOccurrenceId(boolean allDay, DateTime eventStartDate, ZoneId eventStartDateTimeZone) {
    if (allDay) {
      return eventStartDate.toInstant()
                           .atZone(eventStartDateTimeZone)
                           .withZoneSameLocal(ZoneOffset.UTC);
    } else {
      return eventStartDate.toInstant()
                           .atZone(eventStartDateTimeZone)
                           .withZoneSameInstant(ZoneOffset.UTC);
    }
  }

  public static ZonedDateTime getOccurrenceId(boolean allDay, ZonedDateTime eventStartDate, ZoneId eventStartDateTimeZone) {
    if (allDay) {
      return eventStartDate.withZoneSameInstant(eventStartDateTimeZone)
                           .withZoneSameLocal(ZoneOffset.UTC);
    } else {
      return eventStartDate.withZoneSameInstant(eventStartDateTimeZone)
                           .withZoneSameInstant(ZoneOffset.UTC);
    }
  }

  public static Recur getICalendarRecur(EventRecurrence recurrence, ZoneId zoneId) {
    Recur.Builder recurBuilder = new Recur.Builder();
    recurBuilder.frequency(Frequency.valueOf(recurrence.getFrequency().name()));
    recurBuilder.interval(recurrence.getInterval());
    if (recurrence.getCount() > 0) {
      recurBuilder.count(recurrence.getCount() > 0 ? recurrence.getCount() : 0);
    } else if (recurrence.getUntil() != null) {
      ZoneId effectiveZoneId = zoneId != null ? zoneId : ZoneOffset.UTC;
      DateTime dateTime = new DateTime(AgendaDateUtils.toDate(recurrence.getUntil()
        .atStartOfDay(effectiveZoneId)
        .plusDays(1)
        .minusSeconds(1)));
      dateTime.setUtc(true);
      recurBuilder.until(dateTime);
    } else {
      recurBuilder.count(recurrence.getCount() > 0 ? recurrence.getCount() : 0);
    }
    if (recurrence.getBySecond() != null && !recurrence.getBySecond().isEmpty()) {
      NumberList list = new NumberList();
      recurrence.getBySecond().forEach(second -> list.add(Integer.parseInt(second)));
      recurBuilder.secondList(list);
    }
    if (recurrence.getByMinute() != null && !recurrence.getByMinute().isEmpty()) {
      NumberList list = new NumberList();
      recurrence.getByMinute().forEach(minute -> list.add(Integer.parseInt(minute)));
      recurBuilder.minuteList(list);
    }
    if (recurrence.getByHour() != null && !recurrence.getByHour().isEmpty()) {
      NumberList list = new NumberList();
      recurrence.getByHour().forEach(hour -> list.add(Integer.parseInt(hour)));
      recurBuilder.hourList(list);
    }
    if (recurrence.getByDay() != null && !recurrence.getByDay().isEmpty()) {
      WeekDayList list = new WeekDayList();
      recurrence.getByDay().forEach(day -> list.add(new WeekDay(day.toUpperCase())));
      recurBuilder.dayList(list);
    }
    if (recurrence.getByMonthDay() != null && !recurrence.getByMonthDay().isEmpty()) {
      NumberList list = new NumberList();
      recurrence.getByMonthDay().forEach(monthDay -> list.add(Integer.parseInt(monthDay)));
      recurBuilder.monthDayList(list);
    }
    if (recurrence.getByYearDay() != null && !recurrence.getByYearDay().isEmpty()) {
      NumberList list = new NumberList();
      recurrence.getByYearDay().forEach(yearDay -> list.add(Integer.parseInt(yearDay)));
      recurBuilder.yearDayList(list);
    }
    if (recurrence.getByWeekNo() != null && !recurrence.getByWeekNo().isEmpty()) {
      NumberList list = new NumberList();
      recurrence.getByWeekNo().forEach(weekNo -> list.add(Integer.parseInt(weekNo)));
      recurBuilder.weekNoList(list);
    }
    if (recurrence.getByMonth() != null && !recurrence.getByMonth().isEmpty()) {
      MonthList list = new MonthList();
      recurrence.getByMonth().forEach(month -> list.add(new Month(Integer.parseInt(month))));
      recurBuilder.monthList(list);
    }
    if (recurrence.getBySetPos() != null && !recurrence.getBySetPos().isEmpty()) {
      NumberList list = new NumberList();
      recurrence.getBySetPos().forEach(setPos -> list.add(Integer.parseInt(setPos)));
      recurBuilder.setPosList(list);
    }
    return recurBuilder.build();
  }

  /**
   * @param identityManager {@link IdentityManager} service instance
   * @param spaceService {@link SpaceService} service instance
   * @param ownerId calendar owner {@link Identity} technical identifier
   * @param username name of user accessing calendar data
   * @throws IllegalAccessException when the user ACL fails
   */
  public static void checkAclByCalendarOwner(IdentityManager identityManager,
                                             SpaceService spaceService,
                                             long ownerId,
                                             String username) throws IllegalAccessException {
    Identity requestedOwner = identityManager.getIdentity(String.valueOf(ownerId));
    if (requestedOwner == null) {
      throw new IllegalStateException("Calendar owner with id " + ownerId + " wasn't found");
    } else if (requestedOwner.isUser()) {
      if (!StringUtils.equals(requestedOwner.getRemoteId(), username)) {
        throw new IllegalAccessException("User " + username + " is not allowed to retrieve calendar data of user " +
            requestedOwner.getRemoteId());
      }
    } else if (requestedOwner.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
      if (!spaceService.canManageSpace(space, username)) {
        throw new IllegalAccessException("User " + username + " is not allowed to write calendar data of space " +
            space.getDisplayName());
      }
    } else {
      throw new IllegalStateException("Identity with provider type '" + requestedOwner.getProviderId() +
          "' is not managed in calendar owner field");
    }
  }

  /**
   * @param identityManager {@link IdentityManager} service instance
   * @param spaceService {@link SpaceService} service instance
   * @param ownerId calendar owner {@link Identity} technical identifier
   * @param userIdentityId {@link Identity} identifier of user accessing
   *          calendar data
   * @return true if user can create event in a calendar, else return false
   */
  public static boolean canCreateEvent(IdentityManager identityManager,
                                       SpaceService spaceService,
                                       long ownerId,
                                       long userIdentityId) {
    Identity requestedOwner = identityManager.getIdentity(String.valueOf(ownerId));
    if (requestedOwner == null) {
      return false;
    }
    Identity userIdentity = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (userIdentity == null) {
      throw new IllegalStateException("User with id " + userIdentity + " wasn't found");
    } else if (requestedOwner.isUser()) {
      return userIdentityId == Long.parseLong(requestedOwner.getId());
    } else if (requestedOwner.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
      return spaceService.canRedactOnSpace(space, userIdentity.getRemoteId());
    } else {
      return false;
    }
  }

  /**
   * @param identityManager {@link IdentityManager} service instance
   * @param spaceService {@link SpaceService} service instance
   * @param ownerId calendar owner {@link Identity} technical identifier
   * @return true if owner is a space and has at least one redactor, else return
   *         false
   */
  public static boolean canInviteeEdit(IdentityManager identityManager,
                                       SpaceService spaceService,
                                       long ownerId) {
    Identity requestedOwner = identityManager.getIdentity(String.valueOf(ownerId));
    if (requestedOwner == null) {
      return false;
    }

    if (requestedOwner.isUser()) {
      return false;
    } else if (requestedOwner.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
      return space != null && (space.getRedactors() == null || space.getRedactors().length == 0);
    } else {
      return false;
    }
  }

  /**
   * @param identityManager {@link IdentityManager} service instance
   * @param spaceService {@link SpaceService} service instance
   * @param ownerId calendar owner {@link Identity} technical identifier
   * @param userIdentityId {@link Identity} identifier of user accessing
   *          calendar data
   * @return true if user can modify calendar or its events, else return false
   */
  public static boolean canEditCalendar(IdentityManager identityManager,
                                        SpaceService spaceService,
                                        long ownerId,
                                        long userIdentityId) {
    Identity requestedOwner = identityManager.getIdentity(String.valueOf(ownerId));
    if (requestedOwner == null) {
      return false;
    }
    Identity userIdentity = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (userIdentity == null) {
      throw new IllegalStateException("User with id " + userIdentity + " wasn't found");
    }

    if (requestedOwner.isUser()) {
      return userIdentityId == Long.parseLong(requestedOwner.getId());
    } else if (requestedOwner.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
      return spaceService.canManageSpace(space, userIdentity.getRemoteId());
    } else {
      return false;
    }
  }

  /**
   * @param identityManager {@link IdentityManager} service instance
   * @param spaceService {@link SpaceService} service instance
   * @param ownerId calendar owner {@link Identity} technical identifier
   * @param userIdentityId {@link Identity} identifier of user accessing
   *          calendar data
   * @return true if user can access calendar or its events, else return false
   */
  public static boolean canAccessCalendar(IdentityManager identityManager,
                                          SpaceService spaceService,
                                          long ownerId,
                                          long userIdentityId) {
    Identity requestedOwner = identityManager.getIdentity(String.valueOf(ownerId));
    if (requestedOwner == null) {
      return false;
    }

    Identity userIdentity = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (userIdentity == null) {
      throw new IllegalStateException("User with id " + userIdentity + " wasn't found");
    } else if (requestedOwner.isUser()) {
      return userIdentityId == Long.parseLong(requestedOwner.getId());
    } else if (requestedOwner.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
      return spaceService.canViewSpace(space, userIdentity.getRemoteId());
    } else {
      return false;
    }
  }

  public static void broadcastEvent(ListenerService listenerService, String eventName, Object source, Object data) {
    try {
      listenerService.broadcast(eventName, source, data);
    } catch (Exception e) {
      LOG.warn("Error broadcasting event '" + eventName + "' using source '" + source + "' and data " + data, e);
    }
  }

  public static Identity getIdentityById(IdentityManager identityManager, long identityId) {
    return getIdentityById(identityManager, String.valueOf(identityId));
  }

  public static Identity getIdentityById(IdentityManager identityManager, String identityId) {
    return identityManager.getIdentity(identityId);
  }

  public static long getIdentityIdByUsername(IdentityManager identityManager, String username) {
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (identity == null) {
      return 0;
    } else {
      return Long.parseLong(identity.getId());
    }
  }

  public static List<String> getSpaceMembersBySpaceName(String spaceName, SpaceService spaceService) {
    String[] members = spaceService.getSpaceByPrettyName(spaceName).getMembers();
    return Arrays.asList(members);
  }

  public static boolean isEventAttendee(IdentityManager identityManager,
                                        SpaceService spaceService,
                                        long identityId,
                                        EventAttendeeList eventAttendeeList) {
    return isEventAttendee(identityManager, spaceService, identityId, eventAttendeeList.getEventAttendees());
  }

  public static boolean isEventAttendee(IdentityManager identityManager,
                                        SpaceService spaceService,
                                        long identityId,
                                        List<EventAttendee> eventAttendees) {
    Identity userIdentity = identityManager.getIdentity(String.valueOf(identityId));
    if (userIdentity == null) {
      return false;
    } else {
      return eventAttendees != null
             && eventAttendees.stream()
                              .anyMatch(eventAttendee -> {
                                if (identityId == eventAttendee.getIdentityId()) {
                                  return true;
                                } else if (userIdentity.isUser()) {
                                  Identity identity = identityManager.getIdentity(String.valueOf(eventAttendee.getIdentityId()));
                                  if (identity.isSpace()) {
                                    Space space = spaceService.getSpaceByPrettyName(identity.getRemoteId());
                                    return spaceService.canViewSpace(space, userIdentity.getRemoteId());
                                  }
                                }
                                return false;
                              });
    }
  }

  public static net.fortuna.ical4j.model.TimeZone getICalTimeZone(ZoneId zoneId) {
    return ICal4jTimeZoneRegistryHolder.INSTANCE.getTimeZone(zoneId.getId());
  }

  public static ZonedDateTime toDateTime(String dateTimeString, ZoneId userTimeZone) {
    long dateTimeMS = Long.parseLong(dateTimeString);
    ZonedDateTime dateTime = AgendaDateUtils.fromDate(new Date(dateTimeMS));
    return dateTime.withZoneSameLocal(ZoneOffset.UTC).withZoneSameInstant(userTimeZone);
  }

  public static void detectEventModifiedFields(Event newEvent, Event oldEvent, AgendaEventModification eventModification) {
    if (!StringUtils.equals(newEvent.getSummary(), oldEvent.getSummary())) {
      eventModification.addModificationType(AgendaEventModificationType.SUMMARY_UPDATED);
    }
    if (!StringUtils.equals(newEvent.getDescription(), oldEvent.getDescription())) {
      eventModification.addModificationType(AgendaEventModificationType.DESCRIPTION_UPDATED);
    }
    if (!StringUtils.equals(newEvent.getLocation(), oldEvent.getLocation())) {
      eventModification.addModificationType(AgendaEventModificationType.LOCATION_UPDATED);
    }
    if (!StringUtils.equals(newEvent.getColor(), oldEvent.getColor())) {
      eventModification.addModificationType(AgendaEventModificationType.COLOR_UPDATED);
    }
    if (newEvent.isAllDay()) {
      if (!newEvent.getStart()
                   .toLocalDate()
                   .equals(oldEvent.getStart().toLocalDate())) {
        eventModification.addModificationType(AgendaEventModificationType.START_DATE_UPDATED);
      }
      if (!newEvent.getEnd()
                   .toLocalDate()
                   .equals(oldEvent.getEnd().toLocalDate())) {
        eventModification.addModificationType(AgendaEventModificationType.END_DATE_UPDATED);
      }
    } else {
      if (!newEvent.getStart()
                   .withZoneSameInstant(ZoneOffset.UTC)
                   .equals(oldEvent.getStart().withZoneSameInstant(ZoneOffset.UTC))) {
        eventModification.addModificationType(AgendaEventModificationType.START_DATE_UPDATED);
      }
      if (!newEvent.getEnd()
                   .withZoneSameInstant(ZoneOffset.UTC)
                   .equals(oldEvent.getEnd().withZoneSameInstant(ZoneOffset.UTC))) {
        eventModification.addModificationType(AgendaEventModificationType.END_DATE_UPDATED);
      }
    }
    if (newEvent.isAllDay() != oldEvent.isAllDay()) {
      eventModification.addModificationType(AgendaEventModificationType.START_DATE_UPDATED);
      eventModification.addModificationType(AgendaEventModificationType.END_DATE_UPDATED);
    }
    if (newEvent.isAllowAttendeeToUpdate() != oldEvent.isAllowAttendeeToUpdate()) {
      eventModification.addModificationType(AgendaEventModificationType.ALLOW_MODIFY_UPDATED);
    }
    if (newEvent.isAllowAttendeeToInvite() != oldEvent.isAllowAttendeeToInvite()) {
      eventModification.addModificationType(AgendaEventModificationType.ALLOW_INVITE_UPDATED);
    }
    if (newEvent.getCalendarId() != oldEvent.getCalendarId()) {
      eventModification.addModificationType(AgendaEventModificationType.OWNER_UPDATED);
    }
    if (newEvent.getAvailability() != oldEvent.getAvailability()) {
      eventModification.addModificationType(AgendaEventModificationType.AVAILABILITY_UPDATED);
    }
    if (newEvent.getStatus() != oldEvent.getStatus()) {
      eventModification.addModificationType(AgendaEventModificationType.STATUS_UPDATED);
      if (EventStatus.CONFIRMED.equals(newEvent.getStatus()) && EventStatus.TENTATIVE.equals(oldEvent.getStatus())) {
        eventModification.addModificationType(AgendaEventModificationType.SWITCHED_DATE_POLL_TO_EVENT);
      } else if (EventStatus.TENTATIVE.equals(newEvent.getStatus()) && EventStatus.CONFIRMED.equals(oldEvent.getStatus())) {
        eventModification.addModificationType(AgendaEventModificationType.SWITCHED_EVENT_TO_DATE_POLL);
      }
    }
    if (!newEvent.getTimeZoneId().equals(oldEvent.getTimeZoneId())) {
      eventModification.addModificationType(AgendaEventModificationType.TIMEZONE_UPDATED);
    }
    if (!Objects.equals(newEvent.getRecurrence(), oldEvent.getRecurrence())) {
      eventModification.addModificationType(AgendaEventModificationType.RECURRENCE_UPDATED);
    }
  }

  public static String getResourceBundleLabel(Locale locale, String label) {
    ResourceBundleService resourceBundleService = ExoContainerContext.getService(ResourceBundleService.class);
    try {
      return resourceBundleService.getResourceBundle(ArrayUtils.addAll(resourceBundleService.getSharedResourceBundleNames(),
                                                                       "locale.portlet.Agenda"),
                                                     locale)
                                  .getString(label);
    } catch (MissingResourceException mre) {
      return label;
    }
  }

  /**
   * Gets platform language of user. In case of any errors return null.
   *
   * @param userId user Id
   * @return the platform language
   */
  public static String getUserLanguage(String userId) {
    LocaleContextInfo localeCtx = LocaleContextInfoUtils.buildLocaleContextInfo(userId);
    LocalePolicy localePolicy = ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(LocalePolicy.class);
    String lang = Locale.getDefault().getLanguage();
    if(localePolicy != null) {
      Locale locale = localePolicy.determineLocale(localeCtx);
      lang = locale.toString();
    }
    return lang;
  }

  public static boolean isExternal(String userId) {
    IdentityManager identityManager = CommonsUtils.getService(IdentityManager.class);
    org.exoplatform.social.core.identity.model.Identity userIdentity =  identityManager.getOrCreateIdentity(
            OrganizationIdentityProvider.NAME, userId);
    return userIdentity.getProfile() != null && userIdentity.getProfile().getProperty(Profile.EXTERNAL) != null && userIdentity.getProfile().getProperty(Profile.EXTERNAL).equals("true");
  }

  /**
   * Builds the iCalendar document eXo attaches to the mail that announces an
   * event.
   *
   * <p>
   * The document is a <code>PUBLISH</code> one, not a <code>REQUEST</code>:
   * answering an invitation is handled by the tokenised links carried in the
   * mail body, not by iMIP. {@link Method#PUBLISH} is written into the body so
   * it says the same thing as the <code>method=PUBLISH</code> parameter the
   * MIME part already declares, as RFC 6047 asks.
   *
   * @param ownerId identifier of the identity owning the event's calendar,
   *          used to name the space the invitation comes from
   * @param eventSummary event title, written as <code>SUMMARY</code>
   * @param eventDescription event description, HTML as the editor stored it
   * @param startDateRFC3339 event start, RFC 3339
   * @param endDateRFC3339 event end, RFC 3339
   * @param eventConference conference URL, blank when the event has none
   * @param eventModifierId identifier of the identity to write as
   *          <code>ORGANIZER</code>
   * @param eventCreatorFullName display name of whoever sent the invitation
   * @param location event location, blank when the event has none
   * @param userLocale locale of the recipient, the one the labels are read in
   * @param timeZone time zone the dates are written in
   * @return the iCalendar document, UTF-8 encoded
   */
  public static byte[] generateIcsFile(String ownerId,
                                       String eventSummary,
                                       String eventDescription,
                                       String startDateRFC3339,
                                       String endDateRFC3339,
                                       String eventConference,
                                       String eventModifierId,
                                       String eventCreatorFullName,
                                       String location,
                                       Locale userLocale,
                                       ZoneId timeZone) {
    IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
    BrandingService brandingService = ExoContainerContext.getService(BrandingService.class);
    SpaceService spaceService = ExoContainerContext.getService(SpaceService.class);

    Identity identity = identityManager.getIdentity(ownerId);
    Space space = identity!=null ? spaceService.getSpaceByPrettyName(identity.getRemoteId()) : null;
    String spaceName = space == null ? null : space.getDisplayName();

    /* Generate unique identifier */
    UidGenerator ug = new RandomUidGenerator();
    Uid uid = ug.generateUid();
    ZonedDateTime startDate = ZonedDateTime.parse(startDateRFC3339).withZoneSameInstant(timeZone);
    ZonedDateTime endDate = ZonedDateTime.parse(endDateRFC3339).withZoneSameInstant(timeZone);
    net.fortuna.ical4j.model.TimeZone ical4jTimezone = getICalTimeZone(timeZone);
    DateTime startDateTime = new DateTime(Date.from(startDate.toInstant()), ical4jTimezone);
    DateTime endDateTime = new DateTime(Date.from(endDate.toInstant()), ical4jTimezone);
    VEvent vEvent = new VEvent(startDateTime, endDateTime, eventSummary);
    vEvent.getProperties().add(uid);
    /* Create calendar */
    net.fortuna.ical4j.model.Calendar calendar = new net.fortuna.ical4j.model.Calendar();
    // ProdId writes the property name itself: the argument is the value alone,
    // otherwise the wire carries PRODID:PRODID:-//...
    calendar.getProperties().add(new ProdId("-//" + brandingService.getSiteName() + "//" + brandingService.getCompanyName() + "//EN"));
    calendar.getProperties().add(Version.VERSION_2_0);
    calendar.getProperties().add(CalScale.GREGORIAN);
    calendar.getProperties().add(Method.PUBLISH);
    // Explicitly add VTIMEZONE component
    calendar.getComponents().add(ical4jTimezone.getVTimeZone());

    Identity eventOrganizerIdentity = identityManager.getIdentity(eventModifierId);
    if (eventOrganizerIdentity != null) {
      String organizerEmail = eventOrganizerIdentity.getProfile() == null ? null
                                                                         : eventOrganizerIdentity.getProfile().getEmail();
      if (StringUtils.isNotBlank(organizerEmail)) {
        Organizer organizer = new Organizer(toCalendarUserAddress(organizerEmail));
        organizer.getParameters().add(new Cn(eventOrganizerIdentity.getProfile().getFullName()));
        vEvent.getProperties().add(organizer);
      }
    }
    if(StringUtils.isNotBlank(location)) {
      vEvent.getProperties().add(new Location(location));
    }
    URI eventUrl;
    if(StringUtils.isNotBlank(eventConference)) {
      try {
        eventUrl = new URI(eventConference);
        vEvent.getProperties().add(new Url(eventUrl));
      } catch (URISyntaxException use) {
        // Nothing to do, we simply ignore the URL
      }
    }
    HTMLEntityEncoder htmlEntityEncoder = HTMLEntityEncoder.getInstance();
    String htmlContent = "<html><body>" +
            htmlEntityEncoder.encodeHTML(getResourceBundleLabel(userLocale, "agenda.invitationText")) + " " + " <b>" + eventCreatorFullName
            + "</b> " +  htmlEntityEncoder.encodeHTML(getResourceBundleLabel(userLocale, "agenda.inSpace")) + " <b>" + spaceName + "</b>. "
            + ( eventConference != null ? "<br><br><b>" + htmlEntityEncoder.encodeHTML(getResourceBundleLabel(userLocale, "agenda.visioLink")) + " " + "</b> "
            +  "<a href=\""+ eventConference + "\">"
            + eventConference + "</a>" :"");
    if (eventDescription != null && !eventDescription.isEmpty()) {
      htmlContent = htmlContent + "<br><br>" + htmlEntityEncoder.encodeHTML(getResourceBundleLabel(userLocale, "agenda.eventDetail")) + "<br>" + escapeEmoticons(eventDescription);
    }

    htmlContent = htmlContent + "</body></html>";
    //trim and remove all line breaks
    htmlContent = htmlContent.trim().replace("\n", "");
    // DESCRIPTION is plain text by definition; the HTML flavour belongs to
    // X-ALT-DESC alone, built below from the very same content.
    vEvent.getProperties().add(new Description(buildIcsPlainTextDescription(userLocale,
                                                                           eventCreatorFullName,
                                                                           spaceName,
                                                                           eventConference,
                                                                           eventDescription)));
    ParameterList parameters = new ParameterList();
    parameters.add(new net.fortuna.ical4j.model.parameter.XParameter("FMTTYPE", "text/html"));
    XProperty xProperty = new XProperty("X-ALT-DESC", parameters, htmlContent);
    vEvent.getProperties().add(xProperty);

    /* Add event to calendar */
    calendar.getComponents().add(vEvent);
    CalendarOutputter outputter = new CalendarOutputter();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      outputter.output(calendar, output);
      return output.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to convert event '" + eventSummary + "' to iCal format", e);
    }
  }

  /**
   * Turns a bare mail address into the CAL-ADDRESS URI RFC 5545 &sect;3.3.3
   * requires for ORGANIZER and ATTENDEE.
   *
   * <p>
   * A bare address is not a URI: it has no scheme, so a client that validates
   * the value simply drops the property — and with ORGANIZER goes any
   * attribution of the invitation.
   *
   * @param email mail address, already known to be non blank
   * @return the same address as a <code>mailto:</code> URI, left untouched
   *         when it already carries that scheme
   */
  private static URI toCalendarUserAddress(String email) {
    String address = email.trim();
    if (StringUtils.startsWithIgnoreCase(address, "mailto:")) {
      return URI.create(address);
    }
    return URI.create("mailto:" + address);
  }

  /**
   * Builds the plain-text flavour of the invitation blurb, the one
   * <code>DESCRIPTION</code> carries.
   *
   * <p>
   * It is built from the raw labels and values rather than by unescaping the
   * HTML flavour: the HTML one runs every label through
   * {@link HTMLEntityEncoder}, so a French label reaches it as
   * <code>envoy&amp;eacute;e</code>, whose semicolon the iCalendar writer then
   * escapes again into <code>envoy&amp;eacute\;e</code> — an accented word
   * mangled twice over. Reading from the source instead means the value is
   * plain UTF-8 whatever the locale.
   *
   * @param userLocale locale the labels are read in
   * @param eventCreatorFullName display name of whoever sent the invitation
   * @param spaceName display name of the space the event belongs to
   * @param eventConference conference URL, blank when the event has none
   * @param eventDescription event description as HTML, blank when the event
   *          has none
   * @return the description as plain text, with real line breaks
   */
  private static String buildIcsPlainTextDescription(Locale userLocale,
                                                     String eventCreatorFullName,
                                                     String spaceName,
                                                     String eventConference,
                                                     String eventDescription) {
    StringBuilder text = new StringBuilder();
    text.append(getResourceBundleLabel(userLocale, "agenda.invitationText"))
        .append(" ")
        .append(eventCreatorFullName)
        .append(" ")
        .append(getResourceBundleLabel(userLocale, "agenda.inSpace"))
        .append(" ")
        .append(spaceName)
        .append(".");
    if (StringUtils.isNotBlank(eventConference)) {
      text.append("\n\n")
          .append(getResourceBundleLabel(userLocale, "agenda.visioLink"))
          .append(" ")
          .append(eventConference);
    }
    if (StringUtils.isNotBlank(eventDescription)) {
      text.append("\n\n")
          .append(getResourceBundleLabel(userLocale, "agenda.eventDetail"))
          .append("\n")
          .append(htmlToPlainText(eventDescription));
    }
    return text.toString();
  }

  /**
   * Renders an HTML fragment as the plain text a calendar client would show.
   *
   * <p>
   * Block boundaries and <code>&lt;br&gt;</code> become real line breaks,
   * every other tag is dropped and every entity is decoded, so an accented
   * character written as <code>&amp;eacute;</code> comes back as the character
   * itself.
   *
   * @param html HTML fragment, already known to be non blank
   * @return the fragment as plain text
   */
  private static String htmlToPlainText(String html) {
    Document document = Jsoup.parseBodyFragment(html);
    document.outputSettings(new Document.OutputSettings().prettyPrint(false));
    // Jsoup drops the layout with the markup, so the breaks a reader relies on
    // are pinned as text nodes before the text is read out.
    document.select("br").after(LINE_BREAK_MARKER);
    document.select("p, div, li, tr, h1, h2, h3, h4, h5, h6, blockquote, pre").after(LINE_BREAK_MARKER);
    String text = document.body().wholeText().replace(LINE_BREAK_MARKER, "\n").replace('\u00A0', ' ');
    return text.replaceAll("[ \\t]*\\R[ \\t]*", "\n").replaceAll("\n{3,}", "\n\n").trim();
  }

  /**
   * Replaces every non-ASCII character by its HTML numeric entity, so an
   * emoji survives a mail body that is not read as UTF-8.
   *
   * @param text text to escape
   * @return the text with every codepoint above 127 written as an entity
   */
  public static String escapeEmoticons(String text) {
    return text.codePoints()
            .mapToObj(codePoint -> codePoint > 127 ? "&#x" + Integer.toHexString(codePoint) + ";"
                    : new String(Character.toChars(codePoint)))
            .collect(Collectors.joining());
  }

}
