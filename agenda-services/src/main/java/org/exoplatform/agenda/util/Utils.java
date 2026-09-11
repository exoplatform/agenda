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
import java.text.MessageFormat;
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

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.*;
import org.exoplatform.commons.utils.CommonsUtils;
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

  private static final String           GUEST_RESPONSE_TITLE_LABEL     = "agenda.guestResponse.title";

  private static final String           GUEST_RESPONSE_RECORDED_LABEL  = "agenda.guestResponse.recorded";

  private static final String           GUEST_RESPONSE_HINT_LABEL      = "agenda.guestResponse.change";

  /** Browser title of the page shown for an invitation link that has lapsed. */
  private static final String           INVITATION_EXPIRED_TITLE_LABEL   = "agenda.invitationExpired.title";

  /** Headline telling the reader the invitation can no longer be answered. */
  private static final String           INVITATION_EXPIRED_MESSAGE_LABEL = "agenda.invitationExpired.message";

  /** Secondary line explaining that the meeting is over. */
  private static final String           INVITATION_EXPIRED_HINT_LABEL    = "agenda.invitationExpired.hint";

  /** Wording of the anchor offering a way into eXo. */
  private static final String           INVITATION_EXPIRED_LINK_LABEL    = "agenda.invitationExpired.link";

  /**
   * How long a tokenised invitation link outlives the meeting it answers.
   *
   * <p>
   * A constant rather than a configuration property, deliberately. The value
   * ends up inside the token of every invitation mail <b>and</b> inside the
   * DESCRIPTION of every calendar copy, where a byte of difference is a
   * rewrite; a per deployment - or worse, per node - setting would be one more
   * way for two renders of the same event to disagree, for a tuning nobody has
   * asked for. See {@link #invitationTokenExpiry(Event)} for why a day.
   */
  private static final int              INVITATION_TOKEN_GRACE_HOURS     = 24;

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
   * Escapes a string for insertion into an HTML text node.
   *
   * <p>Deliberately not {@code HTMLEntityEncoder}: that encoder escapes ordinary
   * punctuation as well as markup, so a label reading "Your answer has been
   * recorded: Accepted" reached the guest as "recorded&#38;#x3a; Accepted". The five
   * characters below are the ones that can change how a browser parses a text
   * node; everything else is left as the translator wrote it.</p>
   *
   * <p>Escaping is kept rather than dropped even though every value on this page
   * comes from eXo's own resource bundles: those bundles are translated through
   * Crowdin, so their content is only as trusted as the translation pipeline.</p>
   *
   * <p>Package-private rather than private so the escaping can be pinned directly:
   * the page itself renders resource-bundle keys under a bare unit test, which
   * leaves no punctuation in it to assert on.</p>
   *
   * @param text the text to escape, may be null
   * @return the escaped text, or an empty string when the input is null
   */
  static String escapeHtmlText(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
               .replace("<", "&lt;")
               .replace(">", "&gt;")
               .replace("\"", "&quot;")
               .replace("'", "&#39;");
  }

  /**
   * Builds the minimal HTML page acknowledging an answer given through a
   * tokenised invitation link, naming the answer that was recorded.
   *
   * <p>
   * It was introduced for an external attendee - a guest invited by mail
   * address, having no account on the platform - who cannot be redirected to
   * the event page of the portal, which would only display a login form to
   * them (EXO-89705). Since EXO-89753 it serves a second reader with the same
   * problem for a different reason: somebody answering from the description of
   * their calendar copy, on a client which renders no RSVP control of its own.
   * That reader gets no feedback whatsoever from the client they clicked in, so
   * <b>the page has to say what was recorded, not merely that something was</b>
   * - which is why the answer is named in the sentence rather than implied by
   * the page having loaded at all.
   *
   * @param response the {@link EventAttendeeResponse} that has just been
   *          recorded for the attendee
   * @param locale {@link Locale} used to translate the labels of the page, the
   *          default {@link Locale} of the server is used when null
   * @return the HTML content of the confirmation page
   */
  public static String buildGuestResponseConfirmationPage(EventAttendeeResponse response, Locale locale) {
    Locale pageLocale = locale == null ? Locale.getDefault() : locale;
    String responseLabel = getResourceBundleLabel(pageLocale, getResponseLabelKey(response));
    String recorded =
                    escapeHtmlText(MessageFormat.format(getResourceBundleLabel(pageLocale, GUEST_RESPONSE_RECORDED_LABEL),
                                                            responseLabel));
    String hint = escapeHtmlText(getResourceBundleLabel(pageLocale, GUEST_RESPONSE_HINT_LABEL));
    return buildStandaloneAnswerPage(pageLocale, GUEST_RESPONSE_TITLE_LABEL, recorded, hint, null);
  }

  /**
   * Builds the page shown to somebody who follows an invitation link whose
   * meeting is over.
   *
   * <p>
   * The alternative was to let the refusal fall through as a bare 401, which
   * tells its reader nothing at all: they clicked an Accept button in an
   * invitation and got a blank error, with no way to tell whether they had
   * answered, whether the link was broken, or whether they were looking at a
   * fault of their own. This is the same self contained surface the
   * confirmation above uses, saying instead that the invitation can no longer
   * be answered here - and carrying a way into eXo, where the meeting can
   * still be looked at and, if it has not happened yet, still be answered.
   *
   * <p>
   * The link is offered rather than followed. Redirecting a guest, who has no
   * account, would land them on a login form - the exact outcome EXO-89705
   * built this page to avoid.
   *
   * @param locale {@link Locale} used to translate the labels of the page, the
   *          default {@link Locale} of the server is used when null
   * @param eventUrl absolute address of the event inside eXo, blank when the
   *          portal cannot be asked for one, in which case the page simply
   *          carries no link
   * @return the HTML content of the expired invitation page
   */
  public static String buildInvitationExpiredPage(Locale locale, String eventUrl) {
    Locale pageLocale = locale == null ? Locale.getDefault() : locale;
    String message = escapeHtmlText(getResourceBundleLabel(pageLocale, INVITATION_EXPIRED_MESSAGE_LABEL));
    String hint = escapeHtmlText(getResourceBundleLabel(pageLocale, INVITATION_EXPIRED_HINT_LABEL));
    String linkLabel = escapeHtmlText(getResourceBundleLabel(pageLocale, INVITATION_EXPIRED_LINK_LABEL));
    String link = null;
    if (StringUtils.isNotBlank(eventUrl)) {
      link = "<a href=\"" + escapeHtmlText(eventUrl) + "\" style=\"color:#476a9c;\">" + linkLabel + "</a>";
    }
    return buildStandaloneAnswerPage(pageLocale, INVITATION_EXPIRED_TITLE_LABEL, message, hint, link);
  }

  /**
   * The one page shell every standalone invitation outcome is rendered in.
   *
   * <p>
   * Written once so the confirmation and the expiry read as the same page to
   * the same person: they are two endings of one journey, reached from the same
   * button, and a reader who sees both should not be able to tell that two
   * pieces of code drew them.
   *
   * @param pageLocale {@link Locale} to render in, already resolved to a non
   *          null value by the caller
   * @param titleLabelKey resource bundle key of the browser title
   * @param headline the prominent sentence, <b>already HTML escaped</b> by the
   *          caller, since only the caller knows whether it was built by
   *          formatting a message
   * @param hint the secondary sentence, already HTML escaped
   * @param linkHtml a ready made anchor element, or null for a page with no
   *          link; the only argument allowed to carry markup
   * @return the complete HTML document
   */
  private static String buildStandaloneAnswerPage(Locale pageLocale,
                                                  String titleLabelKey,
                                                  String headline,
                                                  String hint,
                                                  String linkHtml) {
    String title = escapeHtmlText(getResourceBundleLabel(pageLocale, titleLabelKey));
    return "<!DOCTYPE html><html lang=\"" + escapeHtmlText(pageLocale.getLanguage()) + "\">"
        + "<head><meta charset=\"utf-8\"/><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
        + "<title>" + title + "</title></head>"
        + "<body style=\"margin:0;padding:40px 20px;background-color:#f5f5f5;"
        + "font-family:HelveticaNeue,Helvetica,Arial,sans-serif;color:#333333;text-align:center;\">"
        + "<p style=\"margin:0 0 12px;font-size:18px;font-weight:bold;\">" + headline + "</p>"
        + "<p style=\"margin:0;font-size:13px;color:#999999;\">" + hint + "</p>"
        + (linkHtml == null ? "" : "<p style=\"margin:16px 0 0;font-size:13px;\">" + linkHtml + "</p>")
        + "</body></html>";
  }

  /**
   * The instant past which a tokenised invitation link for this event stops
   * being honoured.
   *
   * <p>
   * <b>The bound is the meeting itself.</b> A link exists to answer an
   * invitation, and once the meeting is over there is no answer left to give,
   * so nothing legitimate is lost by refusing it - while a link that outlives
   * its meeting stays a usable "answer as this person" credential for anyone
   * the mail was ever forwarded to (EXO-89752).
   *
   * <p>
   * <b>Which end.</b> For a recurring event {@link Event#getEnd()} is the end of
   * the <i>first occurrence</i>, not of the series - the two are stored in
   * different rows and swapped apart in
   * {@link EntityMapper#fromEntity(org.exoplatform.agenda.entity.EventEntity)} -
   * so bounding a series by it would kill the link after its first meeting,
   * while an answer legitimately applies to every occurrence still to come. The
   * series end, {@link EventRecurrence#getOverallEnd()}, is used instead.
   *
   * <p>
   * <b>A series that never ends is already answered, and not here.</b>
   * {@link EntityMapper} stores <code>overallStart.plusYears(10)</code> as the
   * end of an endless recurrence, and reads it back into
   * <code>getOverallEnd()</code>. That is a persisted property of the event
   * rather than a horizon invented in this method, so an endless standup gets
   * ten years of answerable link and this code gains no second opinion about
   * what "no end" means.
   *
   * <p>
   * <b>Why there is a grace period at all.</b> Expiring at the exact end would
   * kill the link in the hand of somebody answering during the meeting's last
   * minute, and would kill it outright for an invitation whose meeting was
   * lengthened after the mail went out. A day covers both, plus any clock skew
   * between the node that minted the token and the node that reads it, and is
   * short enough that a leaked link is not a lasting credential.
   *
   * <p>
   * <b>It is a pure function of the event, and must stay one.</b> The same value
   * has to come out on every render, because the calendar copy writes these
   * links into its DESCRIPTION and the mirror rewrites any copy whose
   * description changed (EXO-89753, EXO-89716). Deriving the bound from "now" -
   * a sliding window from the moment of minting - would make every sweep mint a
   * different token and put every copy into permanent churn. Nothing in this
   * method may read the clock.
   *
   * @param event the {@link Event} the invitation answers, null tolerated
   * @return the expiry as a number of seconds since the epoch, or 0 when the
   *         event carries no date to bound it by - which the caller must treat
   *         as "cannot be answered" rather than as "never expires"
   */
  public static long invitationTokenExpiry(Event event) {
    if (event == null) {
      return 0;
    }
    EventRecurrence recurrence = event.getRecurrence();
    ZonedDateTime reach = recurrence == null ? event.getEnd() : recurrence.getOverallEnd();
    if (reach == null) {
      // A non recurring event with no end, or a recurrence whose overall end
      // was never computed. Fall back to the start rather than to no bound at
      // all: an event is answerable, at the latest, around the time it happens.
      reach = event.getStart();
    }
    if (reach == null) {
      return 0;
    }
    if (event.isAllDay()) {
      // An all day event's end is stored at the start of a day, so honouring it
      // literally would retire the link before the day it belongs to is over.
      // The same widening AgendaDateUtils and getOccurrences already apply.
      ZoneId zoneId = event.getTimeZoneId() == null ? ZoneOffset.UTC : event.getTimeZoneId();
      reach = reach.withZoneSameInstant(zoneId).toLocalDate().atStartOfDay(zoneId).plusDays(1).minusSeconds(1);
    }
    return reach.plusHours(INVITATION_TOKEN_GRACE_HOURS).toEpochSecond();
  }

  /**
   * Gives the resource bundle key holding the human readable label of an
   * invitation answer.
   *
   * @param response the {@link EventAttendeeResponse} to label
   * @return the key of the label inside the <code>locale.portlet.Agenda</code>
   *         resource bundle
   */
  private static String getResponseLabelKey(EventAttendeeResponse response) {
    if (response == EventAttendeeResponse.ACCEPTED) {
      return "agenda.accepted";
    } else if (response == EventAttendeeResponse.DECLINED) {
      return "agenda.declined";
    } else {
      return "agenda.tentative";
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
   * The iCalendar identifier of an event, stable across every mail that
   * describes it and identical for every recipient.
   *
   * <p>
   * A random identifier was minted on each call, so no calendar client could
   * tell that two mails were about the same meeting: an update added a second
   * entry beside the first instead of replacing it, a cancellation matched
   * nothing to cancel, and two attendees held the same meeting under
   * different identifiers. RFC 5545 gives UID exactly this job — naming the
   * event itself — so it is derived from the event rather than invented.
   *
   * <p>
   * The deployment's domain is part of it because the event id alone is only
   * unique within one platform, and two of them would otherwise mint the same
   * identifier for their own event 42 — which the recipient of both would see
   * as one meeting.
   *
   * <p>
   * Note this is deliberately NOT the identifier a user's CalDAV copy carries:
   * that one is per user (each copy is their own object on their own account),
   * while an invitation names the meeting and must read the same to everyone.
   *
   * @param eventId technical identifier of the event, blank when the caller
   *          has none
   * @return the UID to write, falling back to a random one when the event
   *         cannot be named
   */
  private static String icsUid(String eventId) {
    if (StringUtils.isBlank(eventId)) {
      // Nothing to be stable about. A random identifier is still a valid one,
      // and better than an identifier shared by every unnamed event.
      return new RandomUidGenerator().generateUid().getValue();
    }
    String domain = CommonsUtils.getCurrentDomain();
    String host = StringUtils.isBlank(domain) ? "exo" : domain.replaceFirst("^https?://", "").replaceAll("[/:].*$", "");
    return "agenda-event-" + eventId + "@" + host;
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
   * @param eventId identifier of the event, used to derive a UID that is stable
   *          across every mail describing this meeting (EXO-89680)
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
   * @param eventUrl link back to the event in eXo, written as
   *          <code>URL</code> and named in the description. <b>Blank for a
   *          recipient with no eXo account</b>: the link resolves to a login
   *          screen for a guest, so the caller — which is the only party that
   *          knows who the mail is going to — leaves it out for one
   *          (EXO-89751)
   * @param userLocale locale of the recipient, the one the labels are read in
   * @param timeZone time zone the dates are written in
   * @return the iCalendar document, UTF-8 encoded
   */
  public static byte[] generateIcsFile(String eventId,
                                       String ownerId,                                       String eventSummary,
                                       String eventDescription,
                                       String startDateRFC3339,
                                       String endDateRFC3339,
                                       String eventConference,
                                       String eventModifierId,
                                       String eventCreatorFullName,
                                       String location,
                                       String eventUrl,
                                       Locale userLocale,
                                       ZoneId timeZone) {
    IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
    BrandingService brandingService = ExoContainerContext.getService(BrandingService.class);
    SpaceService spaceService = ExoContainerContext.getService(SpaceService.class);

    Identity identity = identityManager.getIdentity(ownerId);
    Space space = identity!=null ? spaceService.getSpaceByPrettyName(identity.getRemoteId()) : null;
    String spaceName = space == null ? null : space.getDisplayName();

    Uid uid = new Uid(icsUid(eventId));
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
        Organizer organizer = new Organizer(EventIcsBuilder.calendarUserAddress(organizerEmail));
        organizer.getParameters().add(new Cn(eventOrganizerIdentity.getProfile().getFullName()));
        vEvent.getProperties().add(organizer);
      }
    }
    if(StringUtils.isNotBlank(location)) {
      vEvent.getProperties().add(new Location(location));
    }
    // URL is "where this event lives" (RFC 5545 §3.8.4.6), so it names the
    // event in eXo. It used to be set from the conference link, which is a
    // different thing entirely and already has its own property — and which
    // left the event's own address out of the document altogether (EXO-89751).
    if (StringUtils.isNotBlank(eventUrl)) {
      try {
        vEvent.getProperties().add(new Url(new URI(eventUrl)));
      } catch (URISyntaxException use) {
        // A link that cannot be parsed is not written; the document is still
        // a valid one without it.
        LOG.debug("Event link {} is not a usable URI; the mailed document carries no URL", eventUrl, use);
      }
    }
    // DESCRIPTION is plain text by definition; the HTML flavour belongs to
    // X-ALT-DESC alone. Both come from EventIcsBuilder, which is also what the
    // CalDAV copy writes, so the two channels attribute the meeting to its
    // space in the very same words (EXO-89732).
    // No answer links in this document, deliberately (EXO-89753). They belong
    // to the calendar copy, whose client may offer no RSVP control of its own;
    // a mail already carries its Accept and Decline buttons in the body. And
    // this method serves the ICS download endpoint as well as the mail, so it
    // has no single recipient a per-person token could be minted for - writing
    // one here would risk handing one attendee the ability to answer as
    // another.
    vEvent.getProperties().add(new Description(EventIcsBuilder.description(userLocale,
                                                                          eventCreatorFullName,
                                                                          spaceName,
                                                                          eventConference,
                                                                          eventUrl,
                                                                          null,
                                                                          eventDescription)));
    String htmlContent = EventIcsBuilder.htmlDescription(userLocale,
                                                         eventCreatorFullName,
                                                         spaceName,
                                                         eventConference,
                                                         eventUrl,
                                                         eventDescription);
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
