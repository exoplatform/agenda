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
import java.time.format.TextStyle;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.*;
import java.util.Date;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.model.*;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.ExoContainerContext;
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
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.component.*;
import net.fortuna.ical4j.model.property.*;

public class Utils {

  private static final Log              LOG                            = ExoLogger.getLogger(Utils.class);

  private static final TimeZoneRegistry ICAL4J_TIME_ZONE_REGISTRY      = TimeZoneRegistryFactory.getInstance().createRegistry();

  public static final String            POST_CREATE_AGENDA_EVENT_EVENT = "exo.agenda.event.created";

  public static final String            POST_UPDATE_AGENDA_EVENT_EVENT = "exo.agenda.event.updated";

  public static final String            POST_DELETE_AGENDA_EVENT_EVENT = "exo.agenda.event.deleted";

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

  public static List<Event> getOccurrences(Event event, LocalDate from, LocalDate to, ZoneId timeZone, int limit) {
    if (timeZone == null) {
      timeZone = ZoneId.systemDefault();
    }

    TimeZone ical4jTimezone = getICalTimeZone(timeZone);
    long startTime = event.isAllDay() ? event.getStart().toLocalDate().atStartOfDay(timeZone).toEpochSecond() * 1000
                                      : event.getStart().toEpochSecond() * 1000;
    long endTime = event.isAllDay() ? event.getEnd()
                                           .toLocalDate()
                                           .atStartOfDay(timeZone)
                                           .plusDays(1)
                                           .minusSeconds(1)
                                           .toEpochSecond()
        * 1000
                                    : event.getEnd().toEpochSecond() * 1000;

    DateTime startDateTime = new DateTime(startTime);
    startDateTime.setTimeZone(ical4jTimezone);
    DateTime endDateTime = new DateTime(endTime);
    endDateTime.setTimeZone(ical4jTimezone);
    VEvent vevent = new VEvent(startDateTime, endDateTime, event.getSummary());
    Recur recur = getICalendarRecur(event, event.getRecurrence());
    vevent.getProperties().add(new RRule(recur));

    long fromTime = from.atStartOfDay(timeZone).toEpochSecond() * 1000;
    if (to == null) {
      ZonedDateTime overallEnd = event.getRecurrence().getOverallEnd();
      if (overallEnd == null) {
        to = from.plusYears(5);
      } else {
        to = overallEnd.withZoneSameInstant(ZoneOffset.UTC).toLocalDate();
      }
    }
    long toTime = to.atStartOfDay(timeZone).plusDays(1).minusSeconds(1).toEpochSecond() * 1000;
    DateTime ical4jFrom = new DateTime(fromTime);
    ical4jFrom.setTimeZone(ical4jTimezone);
    DateTime ical4jTo = new DateTime(toTime);
    ical4jTo.setTimeZone(ical4jTimezone);
    DateList dates = limit > 0 ? recur.getDates(startDateTime, ical4jFrom, ical4jTo, null, limit)
                               : recur.getDates(startDateTime, ical4jFrom, ical4jTo, null);
    if (dates == null || dates.isEmpty()) {
      return Collections.emptyList();
    }
    final ZoneId zoneId = timeZone;
    @SuppressWarnings("all")
    List<LocalDate> occurrencesIds = (List<LocalDate>) dates.stream()
                                                            .map(date -> ((DateTime) date).toInstant()
                                                                                          .atZone(zoneId)
                                                                                          .withZoneSameLocal(ZoneOffset.UTC)
                                                                                          .toLocalDate())
                                                            .collect(Collectors.toList());

    if (limit > 0 && dates.size() >= limit) {
      // Limit period of dates to retrieve of this recurrence to date where we
      // have at maximum 'limit' occurrences that will be retrieved
      ical4jTo = (DateTime) dates.get(limit - 1);
      long duration = endTime - startTime;
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
      ZonedDateTime occurrenceId = occurrencePeriod.getStart().toInstant().atZone(timeZone).withZoneSameLocal(ZoneOffset.UTC);
      if (!occurrencesIds.contains(occurrenceId.toLocalDate())) {
        continue;
      }
      Event occurrence = event.clone();
      occurrence.setId(0);
      occurrence.setStart(occurrencePeriod.getStart().toInstant().atZone(timeZone));
      occurrence.setEnd(occurrencePeriod.getEnd().toInstant().atZone(timeZone));
      occurrence.setOccurrence(new EventOccurrence(occurrenceId, false));
      occurrence.setParentId(event.getId());
      occurrence.setRecurrence(null);
      occurrences.add(occurrence);
    }
    return occurrences;
  }

  public static Recur getICalendarRecur(Event event, EventRecurrence recurrence) {
    try {
      Recur recur = new Recur("FREQ=" + recurrence.getFrequency().name());
      recur.setCount(recurrence.getCount() > 0 ? recurrence.getCount() : 0);
      recur.setInterval(recurrence.getInterval());
      if (recurrence.getUntil() != null) {
        DateTime dateTime = new DateTime(AgendaDateUtils.toDate(recurrence.getUntil()));
        dateTime.setUtc(true);
        recur.setUntil(dateTime);
      }
      if (recurrence.getBySecond() != null && !recurrence.getBySecond().isEmpty()) {
        recurrence.getBySecond().forEach(second -> recur.getSecondList().add(Integer.parseInt(second)));
      }
      if (recurrence.getByMinute() != null && !recurrence.getByMinute().isEmpty()) {
        recurrence.getByMinute().forEach(minute -> recur.getMinuteList().add(Integer.parseInt(minute)));
      }
      if (recurrence.getByHour() != null && !recurrence.getByHour().isEmpty()) {
        recurrence.getByHour().forEach(hour -> recur.getHourList().add(Integer.parseInt(hour)));
      }
      if (recurrence.getByDay() != null && !recurrence.getByDay().isEmpty()) {
        recurrence.getByDay().forEach(day -> recur.getDayList().add(new WeekDay(day.toUpperCase())));
      }
      if (recurrence.getByMonthDay() != null && !recurrence.getByMonthDay().isEmpty()) {
        recurrence.getByMonthDay().forEach(monthDay -> recur.getMonthDayList().add(Integer.parseInt(monthDay)));
      }
      if (recurrence.getByYearDay() != null && !recurrence.getByYearDay().isEmpty()) {
        recurrence.getByYearDay().forEach(yearDay -> recur.getYearDayList().add(Integer.parseInt(yearDay)));
      }
      if (recurrence.getByWeekNo() != null && !recurrence.getByWeekNo().isEmpty()) {
        recurrence.getByWeekNo().forEach(weekNo -> recur.getWeekNoList().add(Integer.parseInt(weekNo)));
      }
      if (recurrence.getByMonth() != null && !recurrence.getByMonth().isEmpty()) {
        recurrence.getByMonth().forEach(month -> recur.getMonthList().add(Integer.parseInt(month)));
      }
      if (recurrence.getBySetPos() != null && !recurrence.getBySetPos().isEmpty()) {
        recurrence.getBySetPos().forEach(setPos -> recur.getSetPosList().add(Integer.parseInt(setPos)));
      }
      return recur;
    } catch (ParseException e) {
      throw new IllegalStateException("Error computing recurrence for event with id " + event.getId(), e);
    }
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
   * @param username name of user accessing calendar data
   * @return true if user can modify calendar or its events, else return false
   */
  public static boolean canEditCalendar(IdentityManager identityManager,
                                        SpaceService spaceService,
                                        long ownerId,
                                        String username) {
    Identity requestedOwner = identityManager.getIdentity(String.valueOf(ownerId));
    if (requestedOwner == null) {
      throw new IllegalStateException("Calendar owner with id " + ownerId + " wasn't found");
    }

    if (StringUtils.equals(OrganizationIdentityProvider.NAME, requestedOwner.getProviderId())) {
      return StringUtils.equals(requestedOwner.getRemoteId(), username);
    } else if (StringUtils.equals(SpaceIdentityProvider.NAME, requestedOwner.getProviderId())) {
      if (spaceService.isSuperManager(username)) {
        return true;
      } else {
        Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
        return spaceService.isManager(space, username);
      }
    } else {
      return false;
    }
  }

  /**
   * @param identityManager {@link IdentityManager} service instance
   * @param spaceService {@link SpaceService} service instance
   * @param ownerId calendar owner {@link Identity} technical identifier
   * @param username name of user accessing calendar data
   * @return true if user can access calendar or its events, else return false
   */
  public static boolean canAccessCalendar(IdentityManager identityManager,
                                          SpaceService spaceService,
                                          long ownerId,
                                          String username) {
    Identity requestedOwner = identityManager.getIdentity(String.valueOf(ownerId));
    if (requestedOwner == null) {
      throw new IllegalStateException("Calendar owner with id " + ownerId + " wasn't found");
    }

    if (StringUtils.equals(OrganizationIdentityProvider.NAME, requestedOwner.getProviderId())) {
      return StringUtils.equals(requestedOwner.getRemoteId(), username);
    } else if (StringUtils.equals(SpaceIdentityProvider.NAME, requestedOwner.getProviderId())) {
      if (spaceService.isSuperManager(username)) {
        return true;
      } else {
        Space space = spaceService.getSpaceByPrettyName(requestedOwner.getRemoteId());
        return spaceService.isMember(space, username);
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
    return identityManager.getIdentity(String.valueOf(identityId));
  }

  public static List<String> getSpaceMembersBySpaceName(String spaceName, SpaceService spaceService) {
    String[] members = spaceService.getSpaceByPrettyName(spaceName).getMembers();
    return Arrays.asList(members);
  }

  public static TimeZone getICalTimeZone(ZoneId zoneId) {
    try {
      TimeZone ical4jTimezone = ICAL4J_TIME_ZONE_REGISTRY.getTimeZone(zoneId.getId());
      if (ical4jTimezone != null) {
        return ical4jTimezone;
      }

      Instant nowInstant = Instant.now();

      ZoneRules zoneIdRules = zoneId.getRules();
      ZoneOffsetTransition nextTransition = zoneIdRules.nextTransition(nowInstant);
      boolean useDaylightSavings = nextTransition != null;

      ZonedDateTime now = ZonedDateTime.now();
      String dtStartValue = AgendaDateUtils.formatDateTimeWithSeconds(now);
      // Properties for Standard component
      PropertyList standardTzProps = new PropertyList();
      TzName standardTzName = new TzName(new ParameterList(), zoneId.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
      DtStart standardTzStart = new DtStart();

      ZoneOffset standardOffset = zoneId.getRules().getStandardOffset(nowInstant);
      long standardOffsetMillis = standardOffset.getTotalSeconds() * 1000l;
      ZoneOffset dstOffset = zoneId.getRules().getOffset(nowInstant);
      long dstOffsetMillis = dstOffset.getTotalSeconds() * 1000l;

      if (useDaylightSavings) {
        LocalDateTime date = getDaylightEnd(zoneId);
        standardTzStart.setValue(AgendaDateUtils.formatDateTimeWithSeconds(date));
      } else {
        standardTzStart.setValue(dtStartValue);
      }

      TzOffsetTo standardTzOffsetTo = new TzOffsetTo();
      standardTzOffsetTo.setOffset(new UtcOffset(standardOffsetMillis));

      TzOffsetFrom standardTzOffsetFrom = new TzOffsetFrom();
      standardTzOffsetFrom.setOffset(new UtcOffset(dstOffsetMillis));

      standardTzProps.add(standardTzName);
      standardTzProps.add(standardTzStart);
      standardTzProps.add(standardTzOffsetTo);
      standardTzProps.add(standardTzOffsetFrom);

      // Standard Component for VTimeZone
      Standard standardTz = new Standard(standardTzProps);

      // Components for VTimeZone
      ComponentList tzComponents = new ComponentList();
      tzComponents.add(standardTz);

      if (useDaylightSavings) {
        // Properties for DayLight component
        PropertyList daylightTzProps = new PropertyList();

        TzName daylightTzName = new TzName(zoneId.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));

        LocalDateTime start = getDaylightStart(zoneId);
        DtStart daylightDtStart = new DtStart();
        daylightDtStart.setValue(AgendaDateUtils.formatDateTimeWithSeconds(start));

        TzOffsetTo daylightTzOffsetTo = new TzOffsetTo();
        daylightTzOffsetTo.setOffset(new UtcOffset(dstOffsetMillis));

        TzOffsetFrom daylightTzOffsetFrom = new TzOffsetFrom();
        daylightTzOffsetFrom.setOffset(new UtcOffset(standardOffset.getTotalSeconds() * 1000l));

        daylightTzProps.add(daylightTzOffsetFrom);
        daylightTzProps.add(daylightTzOffsetTo);
        daylightTzProps.add(daylightDtStart);
        daylightTzProps.add(daylightTzName);

        // Daylight Component for VTimeZone
        Daylight daylightTz = new Daylight(daylightTzProps);
        // add daylight component to VTimeZone
        tzComponents.add(daylightTz);
      }

      PropertyList tzProps = new PropertyList();
      TzId tzId = new TzId(null, zoneId.getId());
      tzProps.add(tzId);

      // Construct the VTimeZone object
      VTimeZone vTz = new VTimeZone(tzProps, tzComponents);
      vTz.validate();
      ical4jTimezone = new TimeZone(vTz);
      ICAL4J_TIME_ZONE_REGISTRY.register(ical4jTimezone);
      return ical4jTimezone;
    } catch (Exception e) {
      LOG.warn("Error computing timezone with zoneId '{}'", zoneId, e);
      return null;
    }
  }

  public static LocalDateTime getDaylightStart(ZoneId zoneId) {
    ZonedDateTime zonedDateTime = LocalDate.of(LocalDate.now().getYear(), 1, 1).atStartOfDay(zoneId);
    ZoneOffsetTransition nextTransition = zoneId.getRules().nextTransition(zonedDateTime.toInstant());
    if (nextTransition.isGap()) {
      return nextTransition.getDateTimeAfter();
    } else {
      ZoneOffsetTransition previousTransition = zoneId.getRules().nextTransition(zonedDateTime.toInstant());
      return previousTransition.getDateTimeAfter();
    }
  }

  public static LocalDateTime getDaylightEnd(ZoneId zoneId) {
    ZonedDateTime zonedDateTime = LocalDate.of(LocalDate.now().getYear(), 1, 1).atStartOfDay(zoneId);
    ZoneOffsetTransition nextTransition = zoneId.getRules().nextTransition(zonedDateTime.toInstant());
    if (nextTransition.isOverlap()) {
      return nextTransition.getDateTimeBefore();
    } else {
      ZoneOffsetTransition previousTransition = zoneId.getRules().nextTransition(zonedDateTime.toInstant());
      return previousTransition.getDateTimeBefore();
    }
  }

  public static ZoneId getUserTimezone(IdentityManager identityManager, long identityId) {
    Identity userIdentity = identityManager.getIdentity(String.valueOf(identityId));
    return getUserTimezone(userIdentity);
  }

  public static ZoneId getUserTimezone(Identity userIdentity) {
    if (userIdentity == null || userIdentity.getProfile().getTimeZone() == null) {
      return ZoneId.systemDefault();
    } else {
      String timeZoneId = userIdentity.getProfile().getTimeZone();
      return java.util.TimeZone.getTimeZone(timeZoneId).toZoneId();
    }
  }

  public static ZonedDateTime toDateTime(String dateTimeString, ZoneId userTimeZone) {
    long dateTimeMS = Long.parseLong(dateTimeString);
    ZonedDateTime dateTime = AgendaDateUtils.fromDate(new Date(dateTimeMS));
    return dateTime.withZoneSameLocal(ZoneOffset.UTC).withZoneSameInstant(userTimeZone);
  }
}
