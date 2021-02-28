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

import java.time.*;
import java.util.*;
import java.util.Date;
import java.util.stream.Collectors;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
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
import net.fortuna.ical4j.model.property.RRule;

public class Utils {

  private static final Log              LOG                            = ExoLogger.getLogger(Utils.class);

  private static final TimeZoneRegistry ICAL4J_TIME_ZONE_REGISTRY      = TimeZoneRegistryFactory.getInstance().createRegistry();

  public static final String            POST_CREATE_AGENDA_EVENT_EVENT = "exo.agenda.event.created";

  public static final String            POST_UPDATE_AGENDA_EVENT_EVENT = "exo.agenda.event.updated";

  public static final String            POST_DELETE_AGENDA_EVENT_EVENT = "exo.agenda.event.deleted";

  public static final String            POST_EVENT_RESPONSE_SENT       = "exo.agenda.event.responseSent";

  public static final String            POST_EVENT_RESPONSE_SAVED      = "exo.agenda.event.responseSaved";

  public static final String            POST_CREATE_AGENDA_EVENT_POLL  = "exo.agenda.event.poll.created";

  public static final String            POST_UPDATE_AGENDA_EVENT_POLL  = "exo.agenda.event.poll.updated";

  public static final String            POST_VOTES_AGENDA_EVENT_POLL   = "exo.agenda.event.poll.voted.all";

  public static final String            POST_VOTE_AGENDA_EVENT_POLL    = "exo.agenda.event.poll.voted";

  public static final String            POST_DISMISS_AGENDA_EVENT_POLL = "exo.agenda.event.poll.dismissed";

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
    ZonedDateTime startTime = event.isAllDay() ? event.getStart().toLocalDate().atStartOfDay(timeZone)
                                               : event.getStart();
    ZonedDateTime endTime = event.isAllDay() ? event.getEnd()
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
    Recur recur = getICalendarRecur(event.getRecurrence(), timeZone);
    vevent.getProperties().add(new RRule(recur));

    ZonedDateTime fromTime = from.atStartOfDay(timeZone);
    if (to == null) {
      ZonedDateTime overallEnd = event.getRecurrence().getOverallEnd();
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
                                                            .map(date -> ((DateTime) date).toInstant()
                                                                                          .atZone(ZoneId.systemDefault())
                                                                                          .withZoneSameInstant(ZoneOffset.UTC)
                                                                                          .toLocalDate())
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
      ZonedDateTime occurrenceId = occurrencePeriod.getStart()
                                                   .toInstant()
                                                   .atZone(ZoneId.systemDefault())
                                                   .withZoneSameInstant(ZoneOffset.UTC);
      if (!occurrencesIds.contains(occurrenceId.toLocalDate())) {
        continue;
      }
      Event occurrence = event.clone();
      occurrence.setId(0);
      occurrence.setStart(occurrencePeriod.getStart().toInstant().atZone(timeZone));
      occurrence.setEnd(occurrencePeriod.getEnd().toInstant().atZone(timeZone));
      occurrence.setOccurrence(new EventOccurrence(occurrenceId, false, false));
      occurrence.setParentId(event.getId());
      occurrence.setRecurrence(null);
      occurrences.add(occurrence);
    }
    return occurrences;
  }

  public static Recur getICalendarRecur(EventRecurrence recurrence, ZoneId zoneId) {
    Recur.Builder recurBuilder = new Recur.Builder();
    recurBuilder.frequency(Frequency.valueOf(recurrence.getFrequency().name()));
    recurBuilder.count(recurrence.getCount() > 0 ? recurrence.getCount() : 0);
    recurBuilder.interval(recurrence.getInterval());
    if (recurrence.getUntil() != null) {
      DateTime dateTime = new DateTime(AgendaDateUtils.toDate(recurrence.getUntil()
                                                                        .atStartOfDay(zoneId)
                                                                        .plusDays(1)
                                                                        .minusSeconds(1)));
      TimeZone ical4jTimezone = getICalTimeZone(zoneId == null ? ZoneOffset.UTC : zoneId);
      dateTime.setTimeZone(ical4jTimezone);
      recurBuilder.until(dateTime);
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
      NumberList list = new NumberList();
      recurrence.getByMonth().forEach(month -> list.add(Integer.parseInt(month)));
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
   * @param readonly whether the access is to read or to write
   * @return true if user can modify calendar, else return false
   * @throws IllegalAccessException when the user ACL fails
   */
  public static boolean checkAclByCalendarOwner(IdentityManager identityManager,
                                                SpaceService spaceService,
                                                long ownerId,
                                                String username,
                                                boolean readonly) throws IllegalAccessException {
    Identity requestedOwner = identityManager.getIdentity(String.valueOf(ownerId));
    if (requestedOwner == null) {
      throw new IllegalStateException("Calendar owner with id " + ownerId + " wasn't found");
    }

    if (StringUtils.equals(OrganizationIdentityProvider.NAME, requestedOwner.getProviderId())) {
      if (!StringUtils.equals(requestedOwner.getRemoteId(), username)) {
        throw new IllegalAccessException("User " + username + " is not allowed to retrieve calendar data of user "
            + requestedOwner.getRemoteId());
      }
      return true;
    } else if (StringUtils.equals(SpaceIdentityProvider.NAME, requestedOwner.getProviderId())) {
      if (spaceService.isSuperManager(username)) {
        return true;
      } else {
        Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
        if (!spaceService.isMember(space, username)) {
          throw new IllegalAccessException("User " + username + " is not allowed to retrieve calendar data of space "
              + requestedOwner.getRemoteId());
        }
        boolean isManager = spaceService.isManager(space, username);
        if (!readonly && !isManager) {
          throw new IllegalAccessException("User " + username + " is not allowed to write calendar data of space "
              + space.getDisplayName());
        }
        return isManager;
      }
    } else {
      throw new IllegalStateException("Identity with provider type '" + requestedOwner.getProviderId()
          + "' is not managed in calendar owner field");
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
    }

    if (StringUtils.equals(OrganizationIdentityProvider.NAME, requestedOwner.getProviderId())) {
      return userIdentityId == Long.parseLong(requestedOwner.getId());
    } else if (StringUtils.equals(SpaceIdentityProvider.NAME, requestedOwner.getProviderId())) {
      boolean superManager = spaceService.isSuperManager(userIdentity.getRemoteId());
      Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
      boolean isMember = space != null && spaceService.isMember(space, userIdentity.getRemoteId());
      boolean isRedactor = space != null && spaceService.isRedactor(space, userIdentity.getRemoteId());
      boolean spaceHasARedactor = space != null && space.getRedactors() != null && space.getRedactors().length > 0;
      return (!spaceHasARedactor || isRedactor) && (superManager || isMember);
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

    if (StringUtils.equals(OrganizationIdentityProvider.NAME, requestedOwner.getProviderId())) {
      return false;
    } else if (StringUtils.equals(SpaceIdentityProvider.NAME, requestedOwner.getProviderId())) {
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

    if (StringUtils.equals(OrganizationIdentityProvider.NAME, requestedOwner.getProviderId())) {
      return userIdentityId == Long.parseLong(requestedOwner.getId());
    } else if (StringUtils.equals(SpaceIdentityProvider.NAME, requestedOwner.getProviderId())) {
      boolean superManager = spaceService.isSuperManager(userIdentity.getRemoteId());
      Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
      boolean isManager = space != null && spaceService.isManager(space, userIdentity.getRemoteId());
      boolean isRedactor = space != null && spaceService.isRedactor(space, userIdentity.getRemoteId());
      boolean spaceHasARedactor = space != null && space.getRedactors() != null && space.getRedactors().length > 0;
      return (!spaceHasARedactor || isRedactor) && (superManager || isManager);
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
    }

    if (StringUtils.equals(OrganizationIdentityProvider.NAME, requestedOwner.getProviderId())) {
      return userIdentityId == Long.parseLong(requestedOwner.getId());
    } else if (StringUtils.equals(SpaceIdentityProvider.NAME, requestedOwner.getProviderId())) {
      if (spaceService.isSuperManager(userIdentity.getRemoteId())) {
        return true;
      } else {
        Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
        return space != null && spaceService.isMember(space, userIdentity.getRemoteId());
      }
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
                                        List<EventAttendee> eventAttendees) {
    Identity userIdentity = identityManager.getIdentity(String.valueOf(identityId));
    if (userIdentity == null) {
      return false;
    }

    return eventAttendees != null
        && eventAttendees.stream().anyMatch(eventAttendee -> {
          if (identityId == eventAttendee.getIdentityId()) {
            return true;
          } else if (StringUtils.equals(userIdentity.getProviderId(), OrganizationIdentityProvider.NAME)) {
            Identity identity = identityManager.getIdentity(String.valueOf(eventAttendee.getIdentityId()));
            if (StringUtils.equals(identity.getProviderId(), SpaceIdentityProvider.NAME)) {
              if (spaceService.isSuperManager(userIdentity.getRemoteId())) {
                return true;
              } else {
                Space space = spaceService.getSpaceByPrettyName(identity.getRemoteId());
                return spaceService.isMember(space, userIdentity.getRemoteId());
              }
            }
          }
          return false;
        });
  }

  public static net.fortuna.ical4j.model.TimeZone getICalTimeZone(ZoneId zoneId) {
    return ICAL4J_TIME_ZONE_REGISTRY.getTimeZone(zoneId.getId());
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
    }
    if (!newEvent.getTimeZoneId().equals(oldEvent.getTimeZoneId())) {
      eventModification.addModificationType(AgendaEventModificationType.TIMEZONE_UPDATED);
    }
    if (!ObjectUtils.equals(newEvent.getRecurrence(), oldEvent.getRecurrence())) {
      eventModification.addModificationType(AgendaEventModificationType.RECURRENCE_UPDATED);
    }
  }
}
