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
package org.exoplatform.agenda.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.storage.AgendaEventConferenceStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Finds the event a call was held for by reading the meeting links stored on
 * events, and keeps only what the reading user may see.
 */
@Service
public class AgendaConferenceEventServiceImpl implements AgendaConferenceEventService {

  public static final String                 MISSING_PROVIDER_TYPE = "agenda.conferenceEvent.missingProviderType";

  public static final String                 MISSING_CALL_ID       = "agenda.conferenceEvent.missingCallId";

  public static final String                 MISSING_MOMENT        = "agenda.conferenceEvent.missingMoment";

  public static final String                 NEGATIVE_GRACE        = "agenda.conferenceEvent.negativeGrace";

  public static final String                 GRACE_TOO_LONG        = "agenda.conferenceEvent.graceTooLong";

  /** The longest grace a caller may ask for: a week. */
  public static final int                    MAX_GRACE_HOURS       = 168;

  /**
   * How long before the moment, beyond the grace, an occurrence may have
   * started and still be looked at: a day's meeting, with half a day to
   * spare. An occurrence that started earlier, such as one of a series of
   * meetings lasting several days, or a stored occurrence moved from a date
   * further back, is not looked at.
   */
  static final Duration                      LOOKBACK_BEYOND_GRACE = Duration.ofHours(36);

  /** At most this many meeting links are read for one call. */
  static final int                           MATCH_LIMIT           = 200;

  /**
   * At most this many occurrences are looked at, the earliest of the window
   * first. The window runs, in the series' time zone, from the start of the
   * day the grace and {@link #LOOKBACK_BEYOND_GRACE} before the moment to the
   * end of the moment's day: up to ten days with the longest grace. A daily
   * or slower series never reaches the limit; a series recurring more often
   * than that in the window, such as a half-hourly one with a grace of a
   * week, has its later occurrences left out, and may then answer an earlier
   * occurrence, or the series.
   */
  static final int                           OCCURRENCE_LIMIT      = 400;

  private static final String                SEGMENT_ENDS          = "?#/";

  private static final Log                   LOG                   =
                                                 ExoLogger.getLogger(AgendaConferenceEventServiceImpl.class);

  private final AgendaEventConferenceStorage conferenceStorage;

  private final AgendaEventService           agendaEventService;

  /**
   * Builds the service.
   *
   * @param conferenceStorage reads the meeting links stored on events
   * @param agendaEventService reads events and occurrences as a user may see
   *          them
   */
  @Autowired
  public AgendaConferenceEventServiceImpl(AgendaEventConferenceStorage conferenceStorage,
                                          AgendaEventService agendaEventService) {
    this.conferenceStorage = conferenceStorage;
    this.agendaEventService = agendaEventService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Event getConferenceEvent(String providerType, String callId, ZonedDateTime moment, int graceHours, long identityId) {
    if (StringUtils.isBlank(providerType)) {
      throw new IllegalArgumentException(MISSING_PROVIDER_TYPE);
    }
    if (StringUtils.isBlank(callId)) {
      throw new IllegalArgumentException(MISSING_CALL_ID);
    }
    if (moment == null) {
      throw new IllegalArgumentException(MISSING_MOMENT);
    }
    if (graceHours < 0) {
      throw new IllegalArgumentException(NEGATIVE_GRACE);
    }
    if (graceHours > MAX_GRACE_HOURS) {
      throw new IllegalArgumentException(GRACE_TOO_LONG);
    }
    Duration grace = Duration.ofHours(graceHours);
    Set<Long> seriesIds = new LinkedHashSet<>();
    conferenceStorage.findConferencesContaining(providerType, "/" + callId, MATCH_LIMIT)
                     .stream()
                     .filter(match -> holdsCallId(match.url(), callId))
                     .forEach(match -> seriesIds.add(match.seriesId()));
    List<Event> spanning = new ArrayList<>();
    List<Event> series = new ArrayList<>();
    seriesIds.forEach(seriesId -> collect(seriesId, providerType, callId, moment, grace, identityId, spanning, series));
    return spanning.stream()
                   .max(Comparator.comparing(Event::getStart))
                   .orElseGet(() -> closestBefore(series, moment));
  }

  /**
   * Tells whether a meeting link holds a call identifier as a whole path
   * segment: right after a <code>/</code>, and followed by the end of the
   * link, a query string, a fragment or another segment. A call whose
   * identifier is the beginning of another's is not taken for it.
   *
   * @param url the meeting link
   * @param callId the call identifier
   * @return true when the link refers to the call
   */
  static boolean holdsCallId(String url, String callId) {
    if (url == null) {
      return false;
    }
    String segment = "/" + callId;
    int index = url.indexOf(segment);
    while (index >= 0) {
      int end = index + segment.length();
      if (end == url.length() || SEGMENT_ENDS.indexOf(url.charAt(end)) >= 0) {
        return true;
      }
      index = url.indexOf(segment, index + 1);
    }
    return false;
  }

  /**
   * Adds the events of one series found for the call, as the user may read
   * them: the event or the occurrences spanning the moment to
   * <code>spanning</code>, and the series itself, or the single event, to
   * <code>series</code> when the user can read it and it holds the call.
   * <p>
   * The occurrences are each read as the user may read them, through
   * {@link AgendaEventService#getEventOccurrence}: a stored occurrence
   * through its own row, which has attendees of its own, so a user invited
   * to that one occurrence only gets it; a computed one through its series.
   * An occurrence counts only when it holds the call itself: a stored one
   * through its own meeting links, which may differ from the series', a
   * computed one through the series'.
   *
   * @param seriesId the series, or the single event
   * @param providerType the type of the call's conference
   * @param callId the call identifier
   * @param moment when the call was held
   * @param grace how long after an occurrence's end the call still belongs
   *          to it
   * @param identityId the reading user's identity
   * @param spanning receives the events spanning the moment
   * @param series receives the readable series and single events
   */
  private void collect(long seriesId,
                       String providerType,
                       String callId,
                       ZonedDateTime moment,
                       Duration grace,
                       long identityId,
                       List<Event> spanning,
                       List<Event> series) {
    Event stored = agendaEventService.getEventById(seriesId);
    if (stored == null) {
      return;
    }
    boolean seriesHoldsCall = holdsCall(seriesId, providerType, callId);
    Event event = seriesHoldsCall ? readEvent(seriesId, identityId) : null;
    if (event != null) {
      series.add(event);
    }
    if (stored.getRecurrence() == null) {
      if (event != null && spans(event, moment, grace)) {
        spanning.add(event);
      }
      return;
    }
    occurrenceIdsAround(stored, moment, grace).stream()
                                       .map(occurrenceId -> readOccurrence(seriesId, occurrenceId, identityId))
                                       .filter(occurrence -> occurrence != null && spans(occurrence, moment, grace))
                                       .filter(occurrence -> occurrence.getId() > 0 ? holdsCall(occurrence.getId(), providerType, callId)
                                                                                    : seriesHoldsCall)
                                       .forEach(spanning::add);
  }

  /**
   * Tells whether an event's own meeting links refer to a call.
   *
   * @param eventId the event, a series or a stored occurrence
   * @param providerType the type of the call's conference
   * @param callId the call identifier
   * @return true when one of the event's conferences of that type holds the
   *         call identifier as a whole path segment
   */
  private boolean holdsCall(long eventId, String providerType, String callId) {
    return conferenceStorage.getEventConferences(eventId)
                            .stream()
                            .anyMatch(conference -> providerType.equals(conference.getType())
                                && holdsCallId(conference.getUrl(), callId));
  }

  /**
   * Computes the dates of the occurrences of a series that may span the
   * moment: those of the days from the grace and
   * {@link #LOOKBACK_BEYOND_GRACE} before it to the day of the moment, the
   * days being read in the series' own time zone, the one the occurrences
   * are computed in. A stored occurrence keeps the date it was computed for,
   * so it is among them even when it was moved within the window.
   *
   * @param series the series, as stored
   * @param moment when the call was held
   * @param grace how long after an occurrence's end the call still belongs
   *          to it
   * @return the occurrence dates, possibly empty
   */
  private static List<ZonedDateTime> occurrenceIdsAround(Event series, ZonedDateTime moment, Duration grace) {
    ZoneId zone = series.getTimeZoneId() == null ? ZoneOffset.UTC : series.getTimeZoneId();
    LocalDate from = moment.minus(grace).minus(LOOKBACK_BEYOND_GRACE).withZoneSameInstant(zone).toLocalDate();
    LocalDate to = moment.withZoneSameInstant(zone).toLocalDate();
    return Utils.getOccurrences(series, from, to, OCCURRENCE_LIMIT)
                .stream()
                .map(occurrence -> occurrence.getOccurrence().getId())
                .toList();
  }

  /**
   * Reads an event as the user may see it.
   *
   * @param eventId the event
   * @param identityId the reading user's identity
   * @return the event, or <code>null</code> when it does not exist, the user
   *         cannot read it, or reads it only as busy time
   */
  private Event readEvent(long eventId, long identityId) {
    try {
      return visible(agendaEventService.getEventById(eventId, ZoneOffset.UTC, identityId));
    } catch (IllegalAccessException e) {
      LOG.debug("Event {} is not readable by identity {}", eventId, identityId, e);
      return null;
    }
  }

  /**
   * Reads an occurrence of a series as the user may see it.
   *
   * @param seriesId the series
   * @param occurrenceId the date identifying the occurrence
   * @param identityId the reading user's identity
   * @return the occurrence, or <code>null</code> when the user cannot read
   *         it, or reads it only as busy time
   */
  private Event readOccurrence(long seriesId, ZonedDateTime occurrenceId, long identityId) {
    try {
      return visible(agendaEventService.getEventOccurrence(seriesId, occurrenceId, ZoneOffset.UTC, identityId));
    } catch (IllegalAccessException e) {
      LOG.debug("Occurrence {} of event {} is not readable by identity {}", occurrenceId, seriesId, identityId, e);
      return null;
    }
  }

  /**
   * Keeps an event the user reads in full.
   *
   * @param event the event as read for the user, or <code>null</code>
   * @return the event, or <code>null</code> when there is none or it is read
   *         only as busy time
   */
  private static Event visible(Event event) {
    return event == null || event.isMasked() ? null : event;
  }

  /**
   * Tells whether an event spans a moment: it is not cancelled, has started
   * by then and had not ended more than the grace period before.
   *
   * @param event the event or occurrence
   * @param moment when the call was held
   * @param grace how long after the end the call still belongs to the event
   * @return true when the call belongs to the event
   */
  private static boolean spans(Event event, ZonedDateTime moment, Duration grace) {
    return event.getStatus() != EventStatus.CANCELLED
        && event.getStart() != null
        && event.getEnd() != null
        && !moment.isBefore(event.getStart())
        && !moment.isAfter(event.getEnd().plus(grace));
  }

  /**
   * Picks, among series and single events, the one started last before the
   * moment, or the first one when all start after it.
   *
   * @param events the readable series and single events
   * @param moment when the call was held
   * @return the event, or <code>null</code> when there is none
   */
  private static Event closestBefore(List<Event> events, ZonedDateTime moment) {
    if (events.isEmpty()) {
      return null;
    }
    return events.stream()
                 .filter(event -> event.getStart() != null && !event.getStart().isAfter(moment))
                 .max(Comparator.comparing(Event::getStart))
                 .orElse(events.get(0));
  }

}
