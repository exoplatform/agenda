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
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.model.*;
import org.exoplatform.commons.utils.CommonsUtils;
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
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.RRule;

public class Utils {

  private static final TimeZoneRegistry ICAL4J_TIME_ZONE_REGISTRY = TimeZoneRegistryFactory.getInstance().createRegistry();

  private static final Log              LOG                       = ExoLogger.getLogger(Utils.class);

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

  public static List<Event> getOccurrences(Event event, LocalDate from, LocalDate to, ZoneId timeZone, int limit) {
    if (timeZone == null) {
      timeZone = ZoneId.systemDefault();
    }

    long startTime = event.getStart().toEpochSecond() * 1000;
    long endTime = event.getEnd().toEpochSecond() * 1000;

    net.fortuna.ical4j.model.TimeZone ical4jTimezone = ICAL4J_TIME_ZONE_REGISTRY.getTimeZone(timeZone.getId());

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
    @SuppressWarnings("all")
    List<LocalDate> occurrencesIds = (List<LocalDate>) dates.stream()
                                                            .map(date -> AgendaDateUtils.fromDate((DateTime) date)
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
      ZonedDateTime occurrenceId = AgendaDateUtils.fromDate(occurrencePeriod.getStart());
      if (!occurrencesIds.contains(occurrenceId.toLocalDate())) {
        continue;
      }
      Event occurrence = event.clone();
      occurrence.setId(0);
      if (event.isAllDay()) {
        occurrence.setStart(occurrencePeriod.getStart().toInstant().atZone(ZoneOffset.UTC).withZoneSameLocal(timeZone));
        occurrence.setEnd(occurrencePeriod.getEnd().toInstant().atZone(ZoneOffset.UTC).withZoneSameLocal(timeZone));
      } else {
        occurrence.setStart(occurrencePeriod.getStart().toInstant().atZone(timeZone));
        occurrence.setEnd(occurrencePeriod.getEnd().toInstant().atZone(timeZone));
      }
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

  public static Identity getIdentityById(long identityId) {
    return getIdentityById(String.valueOf(identityId));
  }

  public static Identity getIdentityById(String identityId) {
    IdentityManager identityManager = CommonsUtils.getService(IdentityManager.class);
    return identityManager.getIdentity(identityId, true);
  }

  public static String getSpaceAvatarByIdSpace(String spaceName) {
    SpaceService spaceService = CommonsUtils.getService(SpaceService.class);
    String avatarUrl = spaceService.getSpaceByPrettyName(spaceName).getAvatarUrl();
    return avatarUrl;
  }
}
