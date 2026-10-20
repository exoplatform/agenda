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
package org.exoplatform.agenda.service;

import static org.exoplatform.agenda.util.NotificationUtils.*;

import java.text.Collator;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.exception.EventInvitationExpiredException;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.plugin.AgendaGuestUserIdentityProvider;
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.util.AttendeesCsv;
import org.exoplatform.agenda.util.DecideContext;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.command.NotificationCommand;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;
import org.exoplatform.social.core.profileproperty.model.ProfilePropertySetting;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.web.security.codec.CodecInitializer;
import org.exoplatform.web.security.security.TokenServiceInitializationException;

public class AgendaEventAttendeeServiceImpl implements AgendaEventAttendeeService {

  private static final Log           LOG       = ExoLogger.getLogger(AgendaEventAttendeeServiceImpl.class);

  private static final String        SEPARATOR            = "@@@";

  /**
   * Position of the expiry inside a decoded token payload.
   *
   * <p>
   * A payload shorter than this is a token minted before EXO-89752, which
   * carries no expiry of its own and is bounded against the event instead.
   */
  private static final int           EXPIRY_TOKEN_PART    = 3;

  /** The refusal of an export on a date poll, which has no participant list. */
  private static final String        EXPORT_DATE_POLL           = "agenda.export.datePoll";

  private static final String        EXPORT_HEADER_NAME         = "agenda.export.header.name";

  private static final String        EXPORT_HEADER_EMAIL        = "agenda.export.header.email";

  private static final String        EXPORT_HEADER_ANSWER       = "agenda.export.header.answer";

  private static final String        EXPORT_HIDDEN              = "agenda.export.hidden";

  private static final String        EXPORT_ANSWER_YES          = "agenda.export.answer.yes";

  private static final String        EXPORT_ANSWER_NO           = "agenda.export.answer.no";

  private static final String        EXPORT_ANSWER_MAYBE        = "agenda.export.answer.maybe";

  private static final String        EXPORT_ANSWER_WAITING_LIST = "agenda.export.answer.waitingList";

  private static final String        EXPORT_ANSWER_NO_ANSWER    = "agenda.export.answer.noAnswer";

  /** Every label of the export, read once per file in the user's locale. */
  private static final List<String>  EXPORT_LABEL_KEYS          = List.of(EXPORT_HEADER_NAME,
                                                                          EXPORT_HEADER_EMAIL,
                                                                          EXPORT_HEADER_ANSWER,
                                                                          EXPORT_HIDDEN,
                                                                          EXPORT_ANSWER_YES,
                                                                          EXPORT_ANSWER_NO,
                                                                          EXPORT_ANSWER_MAYBE,
                                                                          EXPORT_ANSWER_WAITING_LIST,
                                                                          EXPORT_ANSWER_NO_ANSWER);

  private AgendaEventAttendeeStorage attendeeStorage;

  private AgendaEventStorage         eventStorage;

  private ListenerService            listenerService;

  private IdentityManager            identityManager;

  private SpaceService               spaceService;

  private CodecInitializer           codecInitializer;

  private AgendaCalendarService       agendaCalendarService;

  private AgendaCalendarService      calendarService;

  private DecideSteps                decideSteps;

  private AgendaEventService         agendaEventService;

  private ProfilePropertyService     profilePropertyService;

  public AgendaEventAttendeeServiceImpl(AgendaEventAttendeeStorage attendeeStorage,
                                        AgendaEventStorage eventStorage,
                                        AgendaCalendarService calendarService,
                                        ListenerService listenerService,
                                        IdentityManager identityManager,
                                        SpaceService spaceService,
                                        CodecInitializer codecInitializer) {
    this.attendeeStorage = attendeeStorage;
    this.eventStorage = eventStorage;
    this.calendarService = calendarService;
    this.codecInitializer = codecInitializer;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.listenerService = listenerService;
    this.decideSteps = new DecideSteps(eventStorage);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<AgendaEventModificationType> saveEventAttendees(Event event,
                                                             List<EventAttendee> attendees,
                                                             long creatorUserId,
                                                             boolean sendInvitations,
                                                             boolean resetResponses,
                                                             AgendaEventModification eventModifications) {
    return saveEventAttendees(event, attendees, creatorUserId, sendInvitations, resetResponses, eventModifications, true);
  }

  /**
   * Saves the attendees of a date of a series that is over, as a change of the
   * series carries them there: a date over keeps its answers, so the seat a
   * removal frees there goes to nobody.
   *
   * @param date the date's row
   * @param attendees its attendees after the change
   * @param creatorUserId who is saving
   */
  void saveOverDateAttendees(Event date, List<EventAttendee> attendees, long creatorUserId) {
    saveEventAttendees(date, attendees, creatorUserId, false, false, null, false);
  }

  private Set<AgendaEventModificationType> saveEventAttendees(Event event,
                                                              List<EventAttendee> attendees,
                                                              long creatorUserId,
                                                              boolean sendInvitations,
                                                              boolean resetResponses,
                                                              AgendaEventModification eventModifications,
                                                              boolean givesFreedSeats) {
    // The caller hands the event: what is resolved again, when a date moved to
    // another series meanwhile, is only the series to lock
    return decideSteps.resolvingAgainOnce(() -> {
      Event storedEvent = eventStorage.getEventById(event.getId());
      return decideSteps.decide(DecideSteps.targetOf(storedEvent == null ? event : storedEvent),
                                () -> writeEventAttendees(event,
                                                          attendees,
                                                          creatorUserId,
                                                          sendInvitations,
                                                          resetResponses,
                                                          eventModifications,
                                                          givesFreedSeats));
    });
  }

  private Set<AgendaEventModificationType> writeEventAttendees(Event event,
                                                               List<EventAttendee> attendees,
                                                               long creatorUserId,
                                                               boolean sendInvitations,
                                                               boolean resetResponses,
                                                               AgendaEventModification eventModifications,
                                                               boolean givesFreedSeats) {
    long eventId = event.getId();
    EventStatus eventStatus = event.getStatus();
    // The pool's capacity is the stored one, read under the lock, never the
    // caller's copy of the event
    Event storedEvent = eventStorage.getEventById(eventId);
    Event pool = storedEvent == null ? event : storedEvent;

    List<EventAttendee> oldAttendees = getEventAttendees(event.getId()).getEventAttendees();
    List<EventAttendee> newAttendees = attendees == null ? Collections.emptyList() : attendees;

    Set<AgendaEventModificationType> eventModificationTypes = new HashSet<>();

    boolean seatFreed = processAttendeesToDelete(oldAttendees, newAttendees, eventModificationTypes);
    List<Long> seatRequesters = processAttendeesToCreate(pool,
                                                         eventStatus,
                                                         oldAttendees,
                                                         newAttendees,
                                                         creatorUserId,
                                                         resetResponses,
                                                         eventModificationTypes);
    if (resetResponses) {
      resetResponses(eventId, Collections.emptySet());
    } else {
      // A seat the list frees, or the save of the event gives by raising or
      // removing its limit, goes to the waiting list first: the attendees
      // the list adds with a Yes asked after them, and the attendees it
      // removes are not promoted. Rows copied from a series are not decided
      // again
      if (givesFreedSeats && (seatFreed || eventModifications != null
          && eventModifications.hasModification(AgendaEventModificationType.CAPACITY_UPDATED))) {
        promoteWaitingAttendees(eventId);
      }
      for (long seatRequester : seatRequesters) {
        answerPool(pool, null, seatRequester, EventAttendeeResponse.ACCEPTED, false);
      }
    }
    processSendingInvitation(event, attendees, sendInvitations, eventModifications);

    Utils.broadcastEvent(listenerService, "exo.agenda.event.attendees.saved", eventId, 0);

    return eventModificationTypes;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EventAttendeeList getEventAttendees(long eventId) {
    return attendeeStorage.getEventAttendees(eventId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EventAttendeeList getEventAttendees(long eventId, EventAttendeeResponse... responses) {
    return attendeeStorage.getEventAttendees(eventId, responses);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<EventAttendee> getEventAttendees(long eventId,
                                               ZonedDateTime occurrenceId,
                                               EventAttendeeResponse... responses) {
    EventAttendeeList eventAttendeeList = attendeeStorage.getEventAttendees(eventId, responses);
    return eventAttendeeList.getEventAttendees(occurrenceId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EventAttendeeResponse getEventResponse(long eventId,
                                                ZonedDateTime occurrenceId,
                                                long identityId) throws ObjectNotFoundException,
                                                                 IllegalAccessException {
    Event event = eventStorage.getEventById(eventId);
    if (event == null) {
      throw new ObjectNotFoundException("Event with id " + eventId + " wasn't found");
    }
    if (!canRespondToEvent(event, identityId)) {
      throw new IllegalAccessException("User " + identityId + " may not answer event " + eventId
          + ": not an attendee, and the event isn't open to them");
    }
    if (occurrenceId == null && event.getRecurrence() != null) {
      return getSeriesResponse(eventId, identityId);
    }
    EventAttendeeList eventAttendeeList = attendeeStorage.getEventAttendees(eventId, identityId);
    if (eventAttendeeList.isEmpty()) {
      return EventAttendeeResponse.NEEDS_ACTION;
    }
    EventAttendee eventAttendee = eventAttendeeList.getEventAttendee(identityId, occurrenceId);
    return eventAttendee == null ? EventAttendeeResponse.NEEDS_ACTION : eventAttendee.getResponse();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EventAttendeeResponse getSeriesResponse(long seriesId, long identityId) throws ObjectNotFoundException {
    EventAttendee attendee = getSeriesAttendee(seriesId, identityId);
    return attendee == null ? EventAttendeeResponse.NEEDS_ACTION : attendee.getResponse();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EventAttendee getSeriesAttendee(long seriesId, long identityId) throws ObjectNotFoundException {
    Event series = getExistingEvent(seriesId);
    List<EventAttendee> rows = attendeeStorage.getEventAttendees(seriesId, identityId).getEventAttendees(identityId);
    return rowReadAsAWhole(series, rows, () -> seriesReadDate(series));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<EventAttendee> getSeriesAttendees(long seriesId) throws ObjectNotFoundException {
    Event series = getExistingEvent(seriesId);
    List<EventAttendee> rows = attendeeStorage.getEventAttendees(seriesId).getEventAttendees();
    if (series.getRecurrence() == null) {
      return rows;
    }
    Map<Long, List<EventAttendee>> rowsByAttendee = rows.stream()
                                                        .collect(Collectors.groupingBy(EventAttendee::getIdentityId,
                                                                                       LinkedHashMap::new,
                                                                                       Collectors.toList()));
    // The date is read once, and only when an attendee's rows need it
    ZonedDateTime[] readDate = new ZonedDateTime[1];
    Supplier<ZonedDateTime> readDateReader = () -> {
      if (readDate[0] == null) {
        readDate[0] = seriesReadDate(series);
      }
      return readDate[0];
    };
    List<EventAttendee> attendees = new ArrayList<>();
    for (List<EventAttendee> attendeeRows : rowsByAttendee.values()) {
      attendees.add(rowReadAsAWhole(series, attendeeRows, readDateReader));
    }
    return attendees;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EventSeatCounts getSeatCounts(long eventId, ZonedDateTime occurrenceId, long identityId) throws ObjectNotFoundException {
    Event event = getExistingEvent(eventId);
    long poolId = eventId;
    ZonedDateTime date = null;
    if (event.getRecurrence() != null) {
      ZonedDateTime dateRead = occurrenceId == null ? seriesReadDate(event)
                                                    : Utils.getOccurrenceId(event.isAllDay(), occurrenceId, event.getTimeZoneId());
      Event dateRow = dateRead == null ? null : eventStorage.findExceptionalOccurrence(event, dateRead);
      if (dateRow == null) {
        date = dateRead;
      } else {
        // A date with its own row is a pool of its own
        poolId = dateRow.getId();
      }
    }
    ZonedDateTime countedDate = date;
    int acceptedCount = SeatRule.acceptedAt(attendeeStorage.getSeatRows(poolId, EventAttendeeResponse.ACCEPTED),
                                            countedDate,
                                            0);
    List<EventAttendee> waitingRows = attendeeStorage.getWaitingAttendees(poolId)
                                                     .stream()
                                                     .filter(row -> SeatRule.coversDate(row, countedDate))
                                                     .toList();
    Integer waitingListPosition = null;
    for (int i = 0; identityId > 0 && i < waitingRows.size(); i++) {
      if (waitingRows.get(i).getIdentityId() == identityId) {
        waitingListPosition = i + 1;
        break;
      }
    }
    return new EventSeatCounts(acceptedCount, waitingRows.size(), waitingListPosition);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EventAttendeesExport exportEventAttendees(long eventId,
                                                   ZonedDateTime occurrenceId,
                                                   long identityId,
                                                   Locale locale,
                                                   ZoneId zoneId) throws ObjectNotFoundException, IllegalAccessException {
    return exportEventAttendees(eventId,
                                occurrenceId,
                                identityId,
                                locale == null ? Locale.ENGLISH : locale,
                                LocalDate.now(zoneId == null ? ZoneOffset.UTC : zoneId));
  }

  /**
   * Exports the participant list of a date, the file dated with the day given.
   *
   * @param exportDay the day of the export in the user's zone
   * @see #exportEventAttendees(long, ZonedDateTime, long, Locale, ZoneId)
   */
  EventAttendeesExport exportEventAttendees(long eventId,
                                            ZonedDateTime occurrenceId,
                                            long identityId,
                                            Locale locale,
                                            LocalDate exportDay) throws ObjectNotFoundException, IllegalAccessException {
    Event event = getExistingEvent(eventId);
    // The date served, as the event page serves it: the row of the date when
    // it has one, else the series at the date
    Event served = event;
    ZonedDateTime computedDate = null;
    if (event.getRecurrence() != null && occurrenceId != null) {
      ZonedDateTime date = Utils.getOccurrenceId(event.isAllDay(), occurrenceId, event.getTimeZoneId());
      Event dateRow = eventStorage.findExceptionalOccurrence(event, date);
      if (dateRow == null) {
        computedDate = date;
      } else {
        served = dateRow;
      }
    }
    if (served.getStatus() == EventStatus.CANCELLED) {
      // A cancelled date is a date removed from its series
      throw new ObjectNotFoundException("Date " + occurrenceId + " of event " + eventId + " was cancelled");
    }
    if (!getAgendaEventService().canUpdateEvent(served, identityId)) {
      throw new IllegalAccessException("User " + identityId + " may not export the participants of event " + served.getId());
    }
    if (served.getStatus() != EventStatus.CONFIRMED) {
      throw new IllegalArgumentException(EXPORT_DATE_POLL);
    }

    Map<Long, Identity> identities = new HashMap<>();
    Map<Long, String> names = new HashMap<>();
    List<EventAttendee> attendees = new ArrayList<>();
    for (EventAttendee attendee : exportedAttendees(served, computedDate)) {
      Identity identity = identityManager.getIdentity(attendee.getIdentityId());
      if (identity != null) {
        identities.put(attendee.getIdentityId(), identity);
        names.put(attendee.getIdentityId(), exportedName(identity));
        attendees.add(attendee);
      }
    }
    attendees.sort(drawerOrder(served.getCreatorId(), names, locale));

    ProfilePropertySetting emailSetting = getEmailSetting();
    boolean withEmail = emailSetting != null && emailSetting.isActive() && emailSetting.isVisible();
    Map<String, String> labels = new HashMap<>();
    for (String key : EXPORT_LABEL_KEYS) {
      labels.put(key, exportLabel(locale, key));
    }
    List<List<String>> lines = new ArrayList<>(attendees.size() + 1);
    lines.add(withEmail ? List.of(labels.get(EXPORT_HEADER_NAME), labels.get(EXPORT_HEADER_EMAIL), labels.get(EXPORT_HEADER_ANSWER))
                        : List.of(labels.get(EXPORT_HEADER_NAME), labels.get(EXPORT_HEADER_ANSWER)));
    for (EventAttendee attendee : attendees) {
      Identity identity = identities.get(attendee.getIdentityId());
      String answer = labels.get(exportedAnswerKey(identity, attendee.getResponse()));
      lines.add(withEmail ? List.of(names.get(attendee.getIdentityId()),
                                    exportedEmail(identity, attendee.getIdentityId(), emailSetting, labels.get(EXPORT_HIDDEN)),
                                    answer)
                          : List.of(names.get(attendee.getIdentityId()), answer));
    }
    return new EventAttendeesExport(AttendeesCsv.fileName(exportDay, served.getSummary()), AttendeesCsv.write(lines));
  }

  /**
   * @param locale the locale of the user who exports
   * @param key the key of a label of the export
   * @return the label in the Agenda bundle, the key when it holds none
   */
  String exportLabel(Locale locale, String key) {
    return Utils.getResourceBundleLabel(locale, key);
  }

  /**
   * @param served the event, the series or the row of a date served
   * @param computedDate the date of the series served, when it has no row of
   *          its own
   * @return the rows the participants drawer lists for the date served
   * @throws ObjectNotFoundException when the event was deleted meanwhile
   */
  private List<EventAttendee> exportedAttendees(Event served, ZonedDateTime computedDate) throws ObjectNotFoundException {
    if (served.getRecurrence() == null) {
      // An event of no series, or the row of a date, read at its date
      ZonedDateTime rowDate = served.getOccurrence() == null ? null : served.getOccurrence().getId();
      return attendeeStorage.getEventAttendees(served.getId()).getEventAttendees(rowDate);
    } else if (computedDate == null) {
      return getSeriesAttendees(served.getId());
    } else {
      return attendeeStorage.getEventAttendees(served.getId()).getEventAttendees(computedDate);
    }
  }

  /**
   * @return the order of the participants drawer: the creator first, then by
   *         name, the waiting list last in the order its people asked
   */
  private static Comparator<EventAttendee> drawerOrder(long creatorId, Map<Long, String> names, Locale locale) {
    Comparator<ZonedDateTime> askedFirst = Comparator.nullsLast(Comparator.naturalOrder());
    Comparator<EventAttendee> waitingOrder = (attendee1, attendee2) -> isWaiting(attendee1) ? askedFirst.compare(attendee1.getResponseDate(),
                                                                                                                 attendee2.getResponseDate())
                                                                                            : 0;
    return Comparator.comparing((EventAttendee attendee) -> attendee.getIdentityId() != creatorId)
                     .thenComparing(AgendaEventAttendeeServiceImpl::isWaiting)
                     .thenComparing(waitingOrder)
                     .thenComparing(attendee -> names.get(attendee.getIdentityId()), Collator.getInstance(locale));
  }

  private static boolean isWaiting(EventAttendee attendee) {
    return attendee.getResponse() == EventAttendeeResponse.WAITING;
  }

  /**
   * @return a space's name, a guest's address, a user's full name
   */
  private String exportedName(Identity identity) {
    if (identity.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(identity.getRemoteId());
      if (space != null && StringUtils.isNotBlank(space.getDisplayName())) {
        return space.getDisplayName();
      }
    } else if (isGuest(identity)) {
      return identity.getRemoteId();
    }
    Profile profile = identity.getProfile();
    String fullName = profile == null ? null : profile.getFullName();
    return StringUtils.isBlank(fullName) ? identity.getRemoteId() : fullName;
  }

  /**
   * @return nothing for a space, a guest's address, Hidden for a user who hid
   *         their email, else the user's email
   */
  private String exportedEmail(Identity identity, long identityId, ProfilePropertySetting emailSetting, String hidden) {
    if (identity.isSpace()) {
      return "";
    } else if (isGuest(identity)) {
      return identity.getRemoteId();
    } else if (getProfilePropertyService().getHiddenProfilePropertyIds(identityId).contains(emailSetting.getId())) {
      return hidden;
    }
    Profile profile = identity.getProfile();
    return profile == null ? "" : StringUtils.defaultString(profile.getEmail());
  }

  /**
   * @return the key labelling the answer exported: No answer for a space
   *         invited as a whole
   */
  private static String exportedAnswerKey(Identity identity, EventAttendeeResponse response) {
    if (identity.isSpace() || response == null) {
      return EXPORT_ANSWER_NO_ANSWER;
    }
    return switch (response) {
    case ACCEPTED -> EXPORT_ANSWER_YES;
    case DECLINED -> EXPORT_ANSWER_NO;
    case TENTATIVE -> EXPORT_ANSWER_MAYBE;
    case WAITING -> EXPORT_ANSWER_WAITING_LIST;
    default -> EXPORT_ANSWER_NO_ANSWER;
    };
  }

  private static boolean isGuest(Identity identity) {
    return AgendaGuestUserIdentityProvider.NAME.equals(identity.getProviderId());
  }

  /**
   * @return the setting of the email profile property, null when the platform
   *         has none
   */
  private ProfilePropertySetting getEmailSetting() {
    ProfilePropertyService service = getProfilePropertyService();
    return service == null ? null : service.getProfileSettingByName(Profile.EMAIL);
  }

  /**
   * @param series a series, or an event of no series
   * @param rows one attendee's rows on it
   * @param readDate the date the series' answers are read at
   * @return the row read as the attendee's answer to the event as a whole: on
   *         a series, the row valid at that date, else a copy of the
   *         attendee's first row answering
   *         {@link EventAttendeeResponse#NEEDS_ACTION}; null when the attendee
   *         holds no row
   */
  private static EventAttendee rowReadAsAWhole(Event series, List<EventAttendee> rows, Supplier<ZonedDateTime> readDate) {
    if (rows.isEmpty()) {
      return null;
    }
    EventAttendee first = rows.get(0);
    if (series.getRecurrence() == null
        || (rows.size() == 1 && first.getFromOccurrenceId() == null && first.getUntilOccurrenceId() == null)) {
      // One answer to every date: no date to read it at
      return first;
    }
    // An attendee whose rows all start after that date stays listed: a series
    // saved back from the list read would otherwise remove their rows
    return validAt(rows, readDate.get()).orElseGet(() -> withNoAnswer(first));
  }

  private static EventAttendee withNoAnswer(EventAttendee row) {
    EventAttendee attendee = row.clone();
    attendee.setResponse(EventAttendeeResponse.NEEDS_ACTION);
    attendee.setResponseDate(null);
    return attendee;
  }

  /**
   * @return the date the answers to a series as a whole are read at: its next
   *         date, else its last one (see {@link SeriesDates#readDate()})
   */
  private ZonedDateTime seriesReadDate(Event series) {
    return new SeriesDates(series,
                           () -> eventStorage.getExceptionalOccurrences(series.getId()),
                           ZonedDateTime.now(ZoneOffset.UTC)).readDate();
  }

  private Event getExistingEvent(long eventId) throws ObjectNotFoundException {
    Event event = eventStorage.getEventById(eventId);
    if (event == null) {
      throw new ObjectNotFoundException("Event with id " + eventId + " wasn't found");
    }
    return event;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendEventResponse(long eventId,
                                long identityId,
                                EventAttendeeResponse response) throws ObjectNotFoundException, IllegalAccessException {
    sendEventResponse(eventId, identityId, response, true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendUpcomingEventResponse(long eventId,
                                        ZonedDateTime occurrenceId,
                                        long identityId,
                                        EventAttendeeResponse response) throws ObjectNotFoundException, IllegalAccessException {
    sendUpcomingEventResponse(eventId, occurrenceId, identityId, response, true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EventResponseOutcome sendUpcomingEventResponse(long eventId,
                                                        ZonedDateTime occurrenceId,
                                                        long identityId,
                                                        EventAttendeeResponse response,
                                                        boolean broadcast) throws ObjectNotFoundException,
                                                                           IllegalAccessException {
    if (response == null) {
      throw new IllegalArgumentException("Attendee response is mandatory");
    }
    if (occurrenceId == null) {
      throw new IllegalArgumentException("occurrenceId is mandatory");
    }
    if (eventId <= 0) {
      throw new IllegalArgumentException("eventId is mandatory");
    }
    if (identityId <= 0) {
      throw new IllegalArgumentException("identityId is mandatory");
    }
    try {
      return decideSteps.<EventResponseOutcome, ObjectNotFoundException, IllegalAccessException> resolvingAgainOnce(() -> {
        Event event = eventStorage.getEventById(eventId);
        if (event == null) {
          throw new ObjectNotFoundException("Parent event with id " + eventId + " wasn't found");
        }
        if (event.getRecurrence() == null) {
          throw new IllegalStateException("Event with id " + eventId + " isn't recurrent");
        }
        checkCanRespond(event, identityId);
        return decideSteps.decide(DecideSteps.targetOf(event),
                                  () -> writeUpcomingEventResponse(eventId, occurrenceId, identityId, response, broadcast));
      });
    } catch (ResolveAgainException e) {
      throw new ObjectNotFoundException("Parent event with id " + eventId + " wasn't found");
    }
  }

  private EventResponseOutcome writeUpcomingEventResponse(long eventId,
                                                          ZonedDateTime occurrenceId,
                                                          long identityId,
                                                          EventAttendeeResponse response,
                                                          boolean broadcast) {
    Event series = eventStorage.getEventById(eventId);
    // The answer starts from the date as the date's row stores it: the
    // identifier the occurrence list gives an all-day date the clocks start
    // past midnight is past the UTC midnight that row carries
    ZonedDateTime fromDate = Utils.getOccurrenceId(series.isAllDay(), occurrenceId, series.getTimeZoneId());
    EventResponseOutcome outcome = answerSeries(series, fromDate, identityId, response, broadcast);

    // Apply modification on the rows of the dates answered as well: those
    // designating the date answered from, or a later one, read at once, in
    // the order the seat rule ranks the dates
    Event ownRow = null;
    EventAttendeeResponse responseOnOwnRow = null;
    Event earliestWaitingRow = null;
    List<Event> occurrences = rowsByStart(eventId);
    ZonedDateTime decidedAt = ZonedDateTime.now(ZoneOffset.UTC);
    SeriesDates seriesDates = new SeriesDates(series, () -> occurrences, decidedAt);
    for (Event occurrence : occurrences) {
      if (isOnOrAfterSeriesDate(series, occurrenceDateOf(occurrence), fromDate)
          && isEventAttendee(occurrence.getId(), identityId)) {
        EventAttendee propagated = saveEventAttendee(occurrence,
                                                     identityId,
                                                     response,
                                                     false,
                                                     decidesSeat(occurrence, seriesDates),
                                                     decidedAt);
        earliestWaitingRow = earliestWaiting(earliestWaitingRow, occurrence, propagated);
        // The date answered from has a row of its own, a pool deciding alone:
        // the answer stored at that date is the one its row stores, the first
        // created among several
        if (Utils.isSameSeriesDate(series, occurrenceDateOf(occurrence), fromDate)
            && (ownRow == null || occurrence.getId() < ownRow.getId())) {
          ownRow = occurrence;
          responseOnOwnRow = propagated.getResponse();
        }
      }
    }
    return new EventResponseOutcome(ownRow == null ? outcome.response() : responseOnOwnRow,
                                    firstWaiting(series, outcome.firstWaitingOccurrenceId(), earliestWaitingRow));
  }

  /**
   * @param series a series
   * @param occurrenceId the occurrence id of one of its dates, or null
   * @param fromDate the occurrence id of a date answered from
   * @return whether the first designates the date answered from or a later
   *         one, by the date of the series each designates
   *         ({@link Utils#isSameSeriesDate}): a row stored on the UTC day
   *         before its date is the row of that date
   */
  private static boolean isOnOrAfterSeriesDate(Event series, ZonedDateTime occurrenceId, ZonedDateTime fromDate) {
    if (occurrenceId == null) {
      return false;
    }
    if (Utils.hasSeveralDatesADay(series.getRecurrence())) {
      return occurrenceId.toEpochSecond() >= fromDate.toEpochSecond();
    }
    return !Utils.getSeriesDate(series, occurrenceId).isBefore(Utils.getSeriesDate(series, fromDate));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendEventResponse(long eventId,
                                long identityId,
                                EventAttendeeResponse response,
                                boolean broadcast) throws ObjectNotFoundException, IllegalAccessException {
    answerEvent(eventId, identityId, response, broadcast);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EventResponseOutcome sendEventResponse(long eventId,
                                                ZonedDateTime occurrenceId,
                                                long identityId,
                                                EventAttendeeResponse response,
                                                boolean broadcast) throws ObjectNotFoundException,
                                                                   IllegalAccessException {
    if (occurrenceId == null) {
      return answerEvent(eventId, identityId, response, broadcast);
    } else {
      return answerDate(eventId, occurrenceId, identityId, response, broadcast);
    }
  }

  private EventResponseOutcome answerEvent(long eventId,
                                           long identityId,
                                           EventAttendeeResponse response,
                                           boolean broadcast) throws ObjectNotFoundException, IllegalAccessException {
    if (response == null) {
      throw new IllegalArgumentException("Attendee response is mandatory");
    }
    if (eventId <= 0) {
      throw new IllegalArgumentException("eventId is mandatory");
    }
    try {
      return decideSteps.<EventResponseOutcome, ObjectNotFoundException, IllegalAccessException> resolvingAgainOnce(() -> {
        Event event = eventStorage.getEventById(eventId);
        if (event == null) {
          throw new ObjectNotFoundException("Parent event with id " + eventId + " wasn't found");
        }
        checkCanRespond(event, identityId);
        return decideSteps.decide(DecideSteps.targetOf(event),
                                  () -> writeEventResponse(eventId, identityId, response, broadcast));
      });
    } catch (ResolveAgainException e) {
      throw new ObjectNotFoundException("Parent event with id " + eventId + " wasn't found");
    }
  }

  /**
   * Answers one date of a series: under the lock of the series, the date's
   * own row is found, or created as a copy of the series when it has none,
   * and the answer is decided on that row. Two first answers on one date so
   * end on the same row, and a caller never creates the row before it
   * answers.
   */
  private EventResponseOutcome answerDate(long seriesId,
                                          ZonedDateTime occurrenceId,
                                          long identityId,
                                          EventAttendeeResponse response,
                                          boolean broadcast) throws ObjectNotFoundException, IllegalAccessException {
    if (response == null) {
      throw new IllegalArgumentException("Attendee response is mandatory");
    }
    if (seriesId <= 0) {
      throw new IllegalArgumentException("eventId is mandatory");
    }
    try {
      return decideSteps.<EventResponseOutcome, ObjectNotFoundException, IllegalAccessException> resolvingAgainOnce(() -> {
        Event series = eventStorage.getEventById(seriesId);
        if (series == null) {
          throw new ObjectNotFoundException("Parent event with id " + seriesId + " wasn't found");
        }
        if (series.getRecurrence() == null) {
          throw new IllegalStateException("Event with id " + seriesId + " isn't recurrent");
        }
        // Checked before the step, so that a refused caller is told so without
        // a row being written; checked again under the lock on the row answered
        Event knownOccurrence = eventStorage.findExceptionalOccurrence(series,
                                                                       Utils.getOccurrenceId(series.isAllDay(),
                                                                                             occurrenceId,
                                                                                             series.getTimeZoneId()));
        checkCanRespond(knownOccurrence == null ? series : knownOccurrence, identityId);
        return decideSteps.<EventResponseOutcome, ObjectNotFoundException, IllegalAccessException> decide(DecideSteps.targetOf(series), () -> {
          Event occurrence = findOrCreateOccurrence(seriesId, occurrenceId);
          checkCanRespond(occurrence, identityId);
          return writeEventResponse(occurrence.getId(), identityId, response, broadcast);
        });
      });
    } catch (ResolveAgainException e) {
      throw new ObjectNotFoundException("Parent event with id " + seriesId + " wasn't found");
    }
  }

  private Event findOrCreateOccurrence(long seriesId, ZonedDateTime occurrenceId) throws ObjectNotFoundException {
    try {
      return getAgendaEventService().saveEventExceptionalOccurrence(seriesId, occurrenceId);
    } catch (AgendaException e) {
      if (e.getAgendaExceptionType() == AgendaExceptionType.EVENT_NOT_FOUND) {
        throw new ObjectNotFoundException("Parent event with id " + seriesId + " wasn't found");
      }
      throw new IllegalStateException("The date " + occurrenceId + " of event " + seriesId + " can't be answered", e);
    }
  }

  private void checkCanRespond(Event event, long identityId) throws ObjectNotFoundException, IllegalAccessException {
    Identity userIdentity = identityManager.getIdentity(String.valueOf(identityId));
    if (userIdentity == null) {
      throw new ObjectNotFoundException("Identity with id " + identityId + " wasn't found");
    }
    checkNotSubscribedByAnotherOwner(event, identityId);
    if (!canRespondToEvent(event, identityId)) {
      throw new IllegalAccessException("User with identity id " + identityId + " may not answer event with id " + event.getId()
          + ": not an attendee, and the event isn't open to them");
    }
  }

  private EventResponseOutcome writeEventResponse(long eventId,
                                                  long identityId,
                                                  EventAttendeeResponse response,
                                                  boolean broadcast) {
    Event event = eventStorage.getEventById(eventId);
    EventResponseOutcome outcome = answerPool(event, null, identityId, response, broadcast);
    Event earliestWaitingRow = null;

    // Apply modification on exceptional occurrences as well, deciding no seat
    // on a date over, in the order the seat rule ranks the dates. When the
    // series' own dates did not fill and its mark is clear, the first stored
    // date the series reports (confirmed, holding its limit, not over) that
    // the answer fills in that order, its own mark clear, is the one reported
    if (event.getRecurrence() != null) {
      List<Event> occurrences = rowsByStart(eventId);
      ZonedDateTime decidedAt = ZonedDateTime.now(ZoneOffset.UTC);
      SeriesDates seriesDates = new SeriesDates(event, () -> occurrences, decidedAt);
      for (Event occurrence : occurrences) {
        if (isEventAttendee(occurrence.getId(), identityId)) {
          EventAttendee propagated = saveEventAttendee(occurrence,
                                                       identityId,
                                                       response,
                                                       false,
                                                       decidesSeat(occurrence, seriesDates),
                                                       decidedAt);
          earliestWaitingRow = earliestWaiting(earliestWaitingRow, occurrence, propagated);
        }
      }
    }
    return new EventResponseOutcome(outcome.response(), firstWaiting(event, outcome.firstWaitingOccurrenceId(), earliestWaitingRow));
  }

  /**
   * @param occurrence the row of a date of a series an answer to the series is
   *          copied to
   * @param seriesDates the dates of that series
   * @return whether the copy decides a seat there: not on a date over
   */
  private static boolean decidesSeat(Event occurrence, SeriesDates seriesDates) {
    return occurrence.getOccurrence() == null || occurrence.getOccurrence().getId() == null
        || !seriesDates.isOver(occurrence);
  }

  /**
   * @param seriesId the identifier of a series
   * @return the rows of its dates, in the order the seat rule ranks the dates
   *         ({@link #startOfDate(Event)}, the ranking the answer's outcome
   *         uses too): a date moved at its own start, a date not moved at its
   *         id, the time its series had when the row was stored; never at the
   *         start the row stores, anchored on its id's UTC day and a day off
   *         its date where the series' time falls on another UTC day; a row
   *         with no date last
   */
  private List<Event> rowsByStart(long seriesId) {
    return eventStorage.getExceptionalOccurrences(seriesId)
                       .stream()
                       .sorted(Comparator.comparing(this::startOfDate,
                                                    Comparator.nullsLast(Comparator.comparing(ZonedDateTime::toInstant))))
                       .collect(Collectors.toList());
  }

  /**
   * @param current the row of the earliest date left waiting found so far, or
   *          null
   * @param pool a row an answer was stored on
   * @param stored the attendee row stored on it
   * @return the earlier of the current row and the pool, by when their dates
   *         start, a date moved at its own start, when the pool is a date of
   *         a series, not cancelled, that the answer left waiting; else the
   *         current row
   */
  private Event earliestWaiting(Event current, Event pool, EventAttendee stored) {
    if (occurrenceDateOf(pool) == null || stored.getResponse() != EventAttendeeResponse.WAITING
        || pool.getStatus() == EventStatus.CANCELLED) {
      return current;
    }
    return current == null || startOfDate(pool).isBefore(startOfDate(current)) ? pool : current;
  }

  /**
   * @param series the series answered
   * @param seriesDate the first date the answer left waiting on the series'
   *          own rows, or null
   * @param dateRow the row of the earliest date stored on a row of its own
   *          the answer left waiting, or null
   * @return the id of the first date left waiting, by when each starts, a
   *         date moved at its own start
   */
  private ZonedDateTime firstWaiting(Event series, ZonedDateTime seriesDate, Event dateRow) {
    if (dateRow == null) {
      return seriesDate;
    }
    if (seriesDate == null || startOfDate(dateRow).isBefore(startOf(seriesDate, series))) {
      return occurrenceDateOf(dateRow);
    }
    return seriesDate;
  }

  /**
   * @param pool a stored event row
   * @return the occurrence id of the date it is, when it is a date of a
   *         series, else null
   */
  private static ZonedDateTime occurrenceDateOf(Event pool) {
    return pool.getParentId() > 0 && pool.getOccurrence() != null ? pool.getOccurrence().getId() : null;
  }

  /**
   * @param pool a stored event row
   * @return when the date it is starts, when it is a date of a series: at its
   *         id, where the rule puts it, or at its own start when it was moved;
   *         else null
   */
  private ZonedDateTime startOfDate(Event pool) {
    ZonedDateTime occurrenceDate = occurrenceDateOf(pool);
    if (occurrenceDate == null) {
      return null;
    }
    if (pool.getOccurrence().isDatesModified()) {
      return startOf(pool.getStart(), pool);
    }
    // The id of a date not moved was written in its series' zone and length
    // of day, which the date's own zone may have left since
    return startOf(occurrenceDate, eventStorage.getEventById(pool.getParentId()));
  }

  /**
   * @param date the id or the start of a date of an event
   * @param event the row of that date, which says whether it lasts all day,
   *          and in which zone
   * @return when that date starts: an all-day date, whose id and start name
   *         its day in one of the forms
   *         {@link Utils#getOccurrenceId(boolean, ZonedDateTime, ZoneId)}
   *         reads, at the start of that day in the event's zone, so that
   *         dates timed and all day compare
   */
  private static ZonedDateTime startOf(ZonedDateTime date, Event event) {
    if (!event.isAllDay()) {
      return date;
    }
    ZoneId zone = event.getTimeZoneId() == null ? ZoneOffset.UTC : event.getTimeZoneId();
    return Utils.getOccurrenceId(true, date, zone).toLocalDate().atStartOfDay(zone);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void resetEventResponses(long eventId, Set<Long> keptIdentityIds) throws ObjectNotFoundException {
    if (eventId <= 0) {
      throw new IllegalArgumentException("eventId is mandatory");
    }
    Set<Long> kept = keptIdentityIds == null ? Collections.emptySet() : keptIdentityIds;
    try {
      decideSteps.<Void, ObjectNotFoundException, RuntimeException> resolvingAgainOnce(() -> {
        Event event = eventStorage.getEventById(eventId);
        if (event == null) {
          throw new ObjectNotFoundException("Event with id " + eventId + " wasn't found");
        }
        // With nothing to start over, the event is neither locked nor evicted
        // from the cache
        if (!hasResponsesToReset(event, kept)) {
          return null;
        }
        return decideSteps.decide(DecideSteps.targetOf(event), () -> {
          resetResponses(eventId, kept);
          return null;
        });
      });
    } catch (ResolveAgainException e) {
      throw new ObjectNotFoundException("Event with id " + eventId + " wasn't found");
    }
  }

  /**
   * Starts every answer of an event over, on its row and on the rows of its
   * dates when it is a series, the waiting list included. A reset frees no
   * seat: nobody is promoted, and no answer is reported as sent. Runs inside
   * the decide step of its caller, which locked the event.
   *
   * @param eventId the identifier of the event row
   * @param keptIdentityIds the attendees whose answer is kept
   */
  void resetResponses(long eventId, Set<Long> keptIdentityIds) {
    Event event = eventStorage.getEventById(eventId);
    resetPoolResponses(event, keptIdentityIds);
    if (event.getRecurrence() != null) {
      for (long exceptionalOccurenceEventId : eventStorage.getExceptionalOccurenceIds(eventId)) {
        Event occurrence = eventStorage.getEventById(exceptionalOccurenceEventId);
        if (occurrence != null) {
          resetPoolResponses(occurrence, keptIdentityIds);
        }
      }
    }
  }

  /**
   * The attendee rows of the series that continues a series from one of its
   * dates on. Every identity holding a row that covers a date from the split
   * on, whatever the row's start, keeps the part of its rows from that date
   * on, with their answers and answer dates: the rows are read whole, never
   * through the date filter of {@link EventAttendeeList}, which would lose an
   * answer given from a later date. An identity the change adds outside them
   * starts with no answer, and one it removes is left out. Nothing is written.
   *
   * @param seriesId the series split
   * @param splitFrom the occurrence id of the continuation's first date
   * @param addedIdentityIds the identities the change invites
   * @param removedIdentityIds the identities the change removes
   * @return the continuation's rows, with no identifier and no event
   */
  List<EventAttendee> continuationRows(long seriesId,
                                       ZonedDateTime splitFrom,
                                       Collection<Long> addedIdentityIds,
                                       Set<Long> removedIdentityIds) {
    List<EventAttendee> rows = new ArrayList<>();
    Set<Long> identityIds = new HashSet<>();
    for (EventAttendee row : attendeeStorage.getEventAttendees(seriesId).getEventAttendees()) {
      ZonedDateTime until = row.getUntilOccurrenceId();
      if (removedIdentityIds.contains(row.getIdentityId()) || until != null && !until.isAfter(splitFrom)) {
        continue;
      }
      EventAttendee copy = row.clone();
      copy.setId(0);
      copy.setEventId(0);
      if (copy.getFromOccurrenceId() != null && !copy.getFromOccurrenceId().isAfter(splitFrom)) {
        // From the continuation's first date on
        copy.setFromOccurrenceId(null);
      }
      rows.add(copy);
      identityIds.add(row.getIdentityId());
    }
    for (long addedIdentityId : addedIdentityIds) {
      if (!removedIdentityIds.contains(addedIdentityId) && identityIds.add(addedIdentityId)) {
        rows.add(new EventAttendee(0, 0, addedIdentityId, EventAttendeeResponse.NEEDS_ACTION));
      }
    }
    return rows;
  }

  /**
   * Stores the rows {@link #continuationRows} computed under the continuation,
   * as copies: each gets an identifier of its own, the series' rows staying
   * where they are. No answer is reported as saved: the rows carry answers
   * given before. Runs inside the decide step of the split.
   *
   * @param continuationId the continuation's identifier
   * @param rows its rows
   */
  void saveContinuationRows(long continuationId, List<EventAttendee> rows) {
    for (EventAttendee row : rows) {
      EventAttendee copy = row.clone();
      // With an identifier, the storage would move the series' row instead
      copy.setId(0);
      copy.setEventId(continuationId);
      attendeeStorage.saveEventAttendee(copy, continuationId);
    }
  }

  /**
   * Ends the rows of a split series at the date its continuation starts on: a
   * row starting from that date on is removed, and one covering it ends
   * there, as an answer from a date ends the rows before it. Runs inside the
   * decide step of the split.
   *
   * @param seriesId the series split
   * @param splitFrom the occurrence id of the continuation's first date
   */
  void endSeriesRowsAt(long seriesId, ZonedDateTime splitFrom) {
    for (EventAttendee row : attendeeStorage.getEventAttendees(seriesId).getEventAttendees()) {
      ZonedDateTime from = row.getFromOccurrenceId();
      ZonedDateTime until = row.getUntilOccurrenceId();
      if (from != null && !from.isBefore(splitFrom)) {
        attendeeStorage.removeEventAttendee(row.getId());
      } else if (until == null || until.isAfter(splitFrom)) {
        EventAttendee capped = row.clone();
        capped.setUntilOccurrenceId(splitFrom);
        attendeeStorage.saveEventAttendee(capped, seriesId);
      }
    }
  }

  /**
   * Whether a reset would change anything, on the event or, for a series, on
   * the row of one of its dates, where the answers to single dates are.
   */
  private boolean hasResponsesToReset(Event event, Set<Long> keptIdentityIds) {
    if (poolHasResponsesToReset(event, keptIdentityIds)) {
      return true;
    }
    if (event.getRecurrence() == null) {
      return false;
    }
    for (long exceptionalOccurenceEventId : eventStorage.getExceptionalOccurenceIds(event.getId())) {
      Event occurrence = eventStorage.getEventById(exceptionalOccurenceEventId);
      if (occurrence != null && poolHasResponsesToReset(occurrence, keptIdentityIds)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether an attendee not kept holds an answer on a pool, the pool is
   * marked as having reached its capacity, or the series whose mark reports
   * it is: what {@link #resetPoolResponses(Event, Set)} starts over.
   */
  private boolean poolHasResponsesToReset(Event pool, Set<Long> keptIdentityIds) {
    return pool.getCapacityReachedDate() != null
        || markedSeriesReportedThrough(pool) != null
        || attendeeStorage.getEventAttendees(pool.getId())
                          .getEventAttendees()
                          .stream()
                          .anyMatch(row -> !keptIdentityIds.contains(row.getIdentityId())
                              && row.getResponse() != EventAttendeeResponse.NEEDS_ACTION);
  }

  private void resetPoolResponses(Event pool, Set<Long> keptIdentityIds) {
    long eventId = pool.getId();
    Map<Long, List<EventAttendee>> rowsByIdentity = attendeeStorage.getEventAttendees(eventId)
                                                                   .getEventAttendees()
                                                                   .stream()
                                                                   .collect(Collectors.groupingBy(EventAttendee::getIdentityId,
                                                                                                  LinkedHashMap::new,
                                                                                                  Collectors.toList()));
    rowsByIdentity.forEach((identityId, rows) -> {
      if (keptIdentityIds.contains(identityId)
          || rows.stream().allMatch(row -> row.getResponse() == EventAttendeeResponse.NEEDS_ACTION)) {
        return;
      }
      EventAttendee oldAttendee = rows.get(0).clone();
      EventAttendee attendee;
      if (rows.size() > 1) {
        // One answer again, to every date, as an answer to all dates writes it
        rows.forEach(row -> attendeeStorage.removeEventAttendee(row.getId()));
        attendee = new EventAttendee(0, eventId, identityId, EventAttendeeResponse.NEEDS_ACTION);
      } else {
        attendee = rows.get(0);
        attendee.setResponse(EventAttendeeResponse.NEEDS_ACTION);
      }
      attendee.setResponseDate(ZonedDateTime.now(ZoneOffset.UTC));
      attendeeStorage.saveEventAttendee(attendee, eventId);
      Utils.broadcastEvent(listenerService, Utils.POST_EVENT_RESPONSE_SAVED, oldAttendee, attendee);
    });
    if (pool.getCapacityReachedDate() != null) {
      eventStorage.saveCapacityReachedDate(eventId, null);
    }
    // The series' report starts over too when the answers of a date it
    // reports (confirmed, holding its limit, not over) start over (D19, D21),
    // under the series' lock the step holds
    Event markedSeries = markedSeriesReportedThrough(pool);
    if (markedSeries != null) {
      eventStorage.saveCapacityReachedDate(markedSeries.getId(), null);
    }
  }

  /**
   * The series whose standing mark reports a stored date: the date's series,
   * marked, while the date is one the series reports (D21): confirmed,
   * holding the series' limit and not over.
   *
   * @param pool a stored row
   * @return that series, null for a row the series' mark does not report or
   *         whose series is not marked
   */
  private Event markedSeriesReportedThrough(Event pool) {
    if (pool.getParentId() <= 0) {
      return null;
    }
    Event series = eventStorage.getEventById(pool.getParentId());
    return series != null && series.getCapacityReachedDate() != null
        && reportedByTheSeries(pool, series, ZonedDateTime.now(ZoneOffset.UTC)) ? series : null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String generateEncryptedToken(long eventId, String email) {
    return generateEncryptedToken(eventId, email, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String generateEncryptedToken(long eventId,
                                       String emailOrUsername,
                                       EventAttendeeResponse response) {
    if (eventId <= 0) {
      throw new IllegalArgumentException("eventId is mandatory");
    }
    if (StringUtils.isBlank(emailOrUsername)) {
      throw new IllegalArgumentException("email is mandatory");
    }
    long expiry = Utils.invitationTokenExpiry(eventStorage.getEventById(eventId));
    if (expiry <= 0) {
      // No event, or an event carrying no date at all: there is no meeting to
      // bound the link by. Minting one anyway would either be a link that never
      // expires - the defect being fixed - or a link dead on arrival. Neither
      // belongs in a mail, so no link is offered at all.
      LOG.warn("No expiry could be derived for event with id '{}', no invitation token is generated", eventId);
      return null;
    }
    // The answer slot is always written, empty when the token carries no
    // answer, so that the expiry keeps the same position in every token. The
    // reader below relies on it: a three field payload is a token minted before
    // EXO-89752, not a four field one with something missing.
    StringBuilder tokenFlatStringBuilder = new StringBuilder().append(String.valueOf(eventId))
                                                              .append(SEPARATOR)
                                                              .append(emailOrUsername)
                                                              .append(SEPARATOR)
                                                              .append(response == null ? "" : response.getValue())
                                                              .append(SEPARATOR)
                                                              .append(expiry);
    String tokenFlat = tokenFlatStringBuilder.toString();
    try {
      return codecInitializer.getCodec().encode(tokenFlat);
    } catch (TokenServiceInitializationException e) {
      LOG.warn("Error generating Token", e);
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Identity decryptUserIdentity(long eventId,
                                      String token,
                                      EventAttendeeResponse response) throws IllegalAccessException {
    String tokenFlat;
    try {
      tokenFlat = codecInitializer.getCodec().decode(token);
    } catch (TokenServiceInitializationException e) {
      LOG.warn("Error decrypting Token", e);
      return null;
    }
    // -1 keeps the trailing fields of a token whose answer slot is empty, which
    // split() would otherwise drop along with the expiry sitting after it.
    String[] tokenParts = tokenFlat.split("\\" + SEPARATOR, -1);
    if (tokenParts.length < 2) {
      throw new IllegalAccessException("Wrong token format");
    }
    String eventIdString = tokenParts[0];
    if (!eventIdString.equals(String.valueOf(eventId))) {
      throw new IllegalAccessException("Wrong eventId from token");
    }
    if (response != null) {
      String responseString = tokenParts.length > 2 ? StringUtils.defaultIfEmpty(tokenParts[2], null) : null;
      if (!StringUtils.equals(responseString, response.getValue())) {
        throw new IllegalAccessException("Wrong response from token");
      }
    }
    // Deliberately after the event and answer checks: a token repointed at
    // another meeting or another answer must still be refused as forged, and
    // must not be told instead that it merely expired.
    checkInvitationNotExpired(eventId, tokenParts);
    String emailOrUsername = tokenParts[1];
    return getAttendeeIdentity(eventId, emailOrUsername);
  }

  /**
   * Refuses a token whose meeting is over.
   *
   * <p>
   * <b>How tokens minted before EXO-89752 are handled, and why nothing breaks.</b>
   * Every invitation already delivered carries a payload with no expiry in it.
   * Rejecting those outright would break the Accept button of every mail ever
   * sent, and accepting them unchecked would leave the replayable link in place
   * for exactly the population that already has one - so neither is done.
   * A payload with no expiry is bounded by asking the event for the bound now,
   * with {@link Utils#invitationTokenExpiry(Event)}: the same rule, the same
   * grace, computed instead of read.
   *
   * <p>
   * That is what makes this a fix rather than a migration. There is no
   * transition window to configure, no flag to remember to turn off, and no
   * date on which old links stop working in a batch: an old link is answerable
   * for exactly as long as a new one for the same meeting would be, and every
   * link - old and new alike - is bounded from the moment this deploys. The
   * transition ends by itself as the old mails age out.
   *
   * <p>
   * The two are not merely equivalent, and the difference is the reason the
   * field is in the payload at all: a stored expiry pins the bound as it stood
   * when the invitation was issued, so a link cannot be quietly extended by
   * moving the meeting later, whereas a derived one follows the event.
   *
   * @param eventId technical identifier of the {@link Event} being answered,
   *          already checked against the token
   * @param tokenParts the decoded payload, split on the separator
   * @throws EventInvitationExpiredException when the meeting is far enough past
   *           that the link is no longer honoured, or when no bound can be
   *           established at all
   */
  private void checkInvitationNotExpired(long eventId, String[] tokenParts) throws EventInvitationExpiredException {
    long expiry = 0;
    if (tokenParts.length > EXPIRY_TOKEN_PART && StringUtils.isNotBlank(tokenParts[EXPIRY_TOKEN_PART])) {
      try {
        expiry = Long.parseLong(tokenParts[EXPIRY_TOKEN_PART]);
      } catch (NumberFormatException e) {
        // A payload that decoded cleanly cannot have a corrupt expiry unless it
        // was forged. Leaving it at 0 refuses it below.
        LOG.debug("Unparsable expiry in the invitation token of event with id '{}'", eventId, e);
      }
    } else {
      expiry = Utils.invitationTokenExpiry(eventStorage.getEventById(eventId));
    }
    long now = ZonedDateTime.now().toEpochSecond();
    if (expiry <= 0 || now > expiry) {
      throw new EventInvitationExpiredException("Invitation token for event with id '" + eventId + "' expired at '" + expiry
          + "', current time is '" + now + "'");
    }
  }

  /**
   * Resolves the {@link Identity} designated by the identifier carried inside an
   * invitation token. An internal attendee is designated by its username and
   * lives under the {@link OrganizationIdentityProvider}, while an external
   * attendee - a guest invited by mail address only - lives under the
   * {@link AgendaGuestUserIdentityProvider}. The organization provider is tried
   * first so that the behavior of internal attendees is left untouched, then the
   * guest attendees of the designated event are looked up.
   *
   * @param eventId technical identifier of the {@link Event} the token answers
   * @param emailOrUsername username of an internal attendee or mail address of a
   *          guest attendee, as carried by the token
   * @return the {@link Identity} of the attendee, or null when the identifier
   *         designates neither an existing internal user nor a guest attendee of
   *         the event
   */
  private Identity getAttendeeIdentity(long eventId, String emailOrUsername) {
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, emailOrUsername);
    if (identity != null) {
      return identity;
    }
    return getGuestAttendeeIdentity(eventId, emailOrUsername);
  }

  /**
   * Looks up the guest {@link Identity} of a given mail address among the
   * attendees of an event. The lookup is deliberately made on the event
   * attendees and never through
   * {@link IdentityManager#getOrCreateIdentity(String, String)}: the
   * {@link AgendaGuestUserIdentityProvider} accepts any parsable mail address,
   * so asking it to resolve an unknown address would silently create a stray
   * identity for anyone able to forge a token payload.
   *
   * @param eventId technical identifier of the {@link Event} whose attendees are
   *          searched
   * @param email mail address of the guest attendee to look for
   * @return the guest {@link Identity} attending the event with that mail
   *         address, or null when no guest attendee matches
   */
  private Identity getGuestAttendeeIdentity(long eventId, String email) {
    if (StringUtils.isBlank(email)) {
      return null;
    }
    EventAttendeeList eventAttendees = getEventAttendees(eventId);
    if (eventAttendees == null || eventAttendees.getEventAttendees() == null) {
      return null;
    }
    return eventAttendees.getEventAttendees()
                         .stream()
                         .map(eventAttendee -> Utils.getIdentityById(identityManager, eventAttendee.getIdentityId()))
                         .filter(attendeeIdentity -> attendeeIdentity != null
                             && AgendaGuestUserIdentityProvider.NAME.equals(attendeeIdentity.getProviderId())
                             && StringUtils.equalsIgnoreCase(attendeeIdentity.getRemoteId(), email))
                         .findFirst()
                         .orElse(null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isEventAttendee(long eventId, long identityId) { // NOSONAR
    EventAttendeeList eventAttendees = getEventAttendees(eventId);
    return Utils.isEventAttendee(identityManager, spaceService, identityId, eventAttendees);
  }

  /**
   * Replaces how the calendar of an event is read, for the tests; the default
   * resolves the Kernel component on first use.
   *
   * @param agendaCalendarService the calendar service
   */
  public void setAgendaCalendarService(AgendaCalendarService agendaCalendarService) {
    this.agendaCalendarService = agendaCalendarService;
  }

  /**
   * Refuses an answer to an event imported by a subscription somebody else
   * owns — a space's (EXO-90373).
   * <p>
   * Such an event is a copy of a calendar the owner subscribed to: nobody was
   * invited to it, it is read-only everywhere else
   * ({@code AgendaEventServiceImpl#canUpdateEvent}), and an answer recorded on
   * it would be an answer to an invitation that does not exist. The owner of a
   * personal subscription is left alone: their own imported events carry their
   * attendee row by design (EXO-90278). The calendar service is a Kernel
   * component reached on first use; when it cannot be reached the answer is
   * allowed, as it was before this guard — the attendee check below is what
   * refuses it in fact, since a space's imported event has no attendee at all.
   *
   * @param event the event being answered
   * @param identityId identity identifier of the user answering
   * @throws IllegalAccessException when the event is another owner's imported copy
   */
  private void checkNotSubscribedByAnotherOwner(Event event, long identityId) throws IllegalAccessException {
    if (agendaCalendarService == null) {
      agendaCalendarService = ExoContainerContext.getService(AgendaCalendarService.class);
    }
    if (agendaCalendarService == null) {
      return;
    }
    Calendar calendar = agendaCalendarService.getCalendarById(event.getCalendarId());
    if (calendar != null && calendar.isSubscription() && calendar.getOwnerId() != identityId) {
      throw new IllegalAccessException("Event " + event.getId() + " comes from a calendar subscription of identity "
          + calendar.getOwnerId() + ": user " + identityId + " cannot answer it");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean canRespondToEvent(Event event, long identityId) {
    if (event == null || identityId <= 0) {
      return false;
    }
    // The attendee half stays per event: on an exceptional occurrence, its own
    // attendee list. Resolving it to the series would let a participant removed
    // from one occurrence answer on it even on a locked series (US05)
    if (isEventAttendee(event.getId(), identityId)) {
      return true;
    }
    if (!isOpen(event)) {
      return false;
    }
    Calendar calendar = calendarService.getCalendarById(event.getCalendarId());
    return Utils.canAccessEventCalendar(identityManager, spaceService, calendar, identityId);
  }

  /**
   * Since US06 the open flag is a property of each row: a date individually
   * modified answers with its own value, which a series change merges into but
   * never overwrites when it was customised there. The event this reads is
   * always one that has a row — a computed occurrence is never stored and
   * never reaches a response.
   *
   * @param event {@link Event} as stored
   * @return whether that event is open to the members of its space
   */
  private boolean isOpen(Event event) {
    return Boolean.TRUE.equals(event.getOpen());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendInvitations(Event event, List<EventAttendee> eventAttendees, AgendaEventModification eventModifications) {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(EVENT_AGENDA, event);
    ctx.append(EVENT_ATTENDEE, eventAttendees);
    ctx.append(EVENT_MODIFIER, eventModifications.getModifierId());

    if (eventModifications.hasModification(AgendaEventModificationType.DELETED)) {
      ctx.append(EVENT_MODIFICATION_TYPE, AgendaEventModificationType.DELETED.name());
    } else if (eventModifications.hasModification(AgendaEventModificationType.ADDED)) {
      ctx.append(EVENT_MODIFICATION_TYPE, AgendaEventModificationType.ADDED.name());
    } else if (eventModifications.hasModification(AgendaEventModificationType.SWITCHED_EVENT_TO_DATE_POLL)) {
      ctx.append(EVENT_MODIFICATION_TYPE, AgendaEventModificationType.SWITCHED_EVENT_TO_DATE_POLL.name());
    } else if (eventModifications.hasModification(AgendaEventModificationType.SWITCHED_DATE_POLL_TO_EVENT)) {
      ctx.append(EVENT_MODIFICATION_TYPE, AgendaEventModificationType.SWITCHED_DATE_POLL_TO_EVENT.name());
    } else if (eventModifications.hasModification(AgendaEventModificationType.START_DATE_UPDATED)
        || eventModifications.hasModification(AgendaEventModificationType.END_DATE_UPDATED)) {
      ctx.append(EVENT_MODIFICATION_TYPE, AgendaEventModificationType.DATES_UPDATED.name());
    } else if (eventModifications.hasModification(AgendaEventModificationType.UPDATED)) {
      ctx.append(EVENT_MODIFICATION_TYPE, AgendaEventModificationType.UPDATED.name());
    }

    if (eventModifications.hasModification(AgendaEventModificationType.DELETED)) {
      dispatch(ctx, AGENDA_EVENT_CANCELLED_NOTIFICATION_PLUGIN);
    } else if (eventModifications.hasModification(AgendaEventModificationType.UPDATED)) {
      dispatch(ctx, AGENDA_EVENT_MODIFIED_NOTIFICATION_PLUGIN);
    } else if (eventModifications.hasModification(AgendaEventModificationType.ADDED)
        && event.getStatus() == EventStatus.TENTATIVE) {
      dispatch(ctx, AGENDA_DATE_POLL_NOTIFICATION_PLUGIN);
    } else if (eventModifications.hasModification(AgendaEventModificationType.ADDED)
        && event.getStatus() == EventStatus.CONFIRMED) {
      dispatch(ctx, AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN);
    }
  }

  /**
   * Stores an answer of an attendee on a pool: on the row of a series, date by
   * date (see {@link #answerSeries}); on the row of an event of no series or
   * of a date of a series, as {@link #saveEventAttendee} does.
   *
   * @param pool the stored row answered, as read under the lock
   * @param fromDate the date a series answer starts from, null for an answer
   *          to every date of the row
   * @param identityId the attendee's identity identifier
   * @param requested the answer given
   * @param userResponseSent whether the attendee sent it, reported as such
   * @return the answer stored at the date answered, and the first date of a
   *         series it left waiting
   */
  private EventResponseOutcome answerPool(Event pool,
                                          ZonedDateTime fromDate,
                                          long identityId,
                                          EventAttendeeResponse requested,
                                          boolean userResponseSent) {
    if (pool.getRecurrence() != null) {
      return answerSeries(pool, fromDate, identityId, requested, userResponseSent);
    }
    EventAttendee stored = saveEventAttendee(pool, identityId, requested, userResponseSent, true, ZonedDateTime.now(ZoneOffset.UTC));
    Event waitingRow = earliestWaiting(null, pool, stored);
    return new EventResponseOutcome(stored.getResponse(), waitingRow == null ? null : occurrenceDateOf(waitingRow));
  }

  /**
   * Stores an answer of an attendee on the row of a series, for every date of
   * it or from one date on, decided date by date (D17). The range answered is
   * cut into stretches at the bounds of the rows answering Yes, over which the
   * seats taken stay the same; each stretch is decided at its first counted
   * date (see {@link SeriesDates}), a stretch with none keeping the seat the
   * attendee held over it, else taking the decision of its counted
   * neighbour, and the runs with the same outcome are stored as
   * pieces, one row each, in ascending order. An answer from one date on
   * removes the attendee's rows starting from it and caps the one covering
   * it, which keeps its answer date; an answer to every date replaces all of
   * the attendee's rows, a lone row losing its range. A piece keeps an answer
   * date only where the stored answer does not change (see
   * {@link SeatRule#pieceResponseDate}).
   * <p>
   * A seat the answer gives up promotes the waiting list in the same
   * transaction. The first counted date it fills while the series is not
   * marked marks it. The answer is reported once, as saved and, when the
   * attendee sent it, as sent: the piece valid at the date answered from, or,
   * for every date, at the date the answer to the series is read at.
   *
   * @param series the row of the series, as read under the lock
   * @param fromDate the date the answer starts from, normalised, or null for
   *          every date
   * @param identityId the attendee's identity identifier
   * @param requested the answer given
   * @param userResponseSent whether the attendee sent it, reported as such
   * @return the answer stored at the date read, and the first counted date a
   *         piece left waiting
   */
  private EventResponseOutcome answerSeries(Event series,
                                            ZonedDateTime fromDate,
                                            long identityId,
                                            EventAttendeeResponse requested,
                                            boolean userResponseSent) {
    long seriesId = series.getId();
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    SeriesDates seriesDates = new SeriesDates(series, () -> eventStorage.getExceptionalOccurrences(seriesId), now);
    List<EventAttendee> priorRows = attendeeStorage.getEventAttendees(seriesId, identityId)
                                                   .getEventAttendees(identityId)
                                                   .stream()
                                                   .map(EventAttendee::clone)
                                                   .toList();
    int capacity = SeatRule.capacityOf(series);
    ZonedDateTime[] filledDate = new ZonedDateTime[1];
    List<SeatPiece> pieces;
    if (capacity > 0 && SeatRule.asksForSeat(requested)) {
      List<EventAttendee> acceptedRows = attendeeStorage.getSeatRows(seriesId, EventAttendeeResponse.ACCEPTED);
      pieces = SeatRule.pieces(SeatRule.stretches(acceptedRows, fromDate, null),
                               stretch -> seriesDates.firstCountedDate(stretch.from(), stretch.until()),
                               date -> {
                                 int acceptedByOthers = SeatRule.acceptedAt(acceptedRows, date, identityId);
                                 EventAttendeeResponse decided = SeatRule.decide(requested,
                                                                                 heldAt(priorRows, date),
                                                                                 capacity,
                                                                                 acceptedByOthers);
                                 if (filledDate[0] == null && decided == EventAttendeeResponse.ACCEPTED
                                     && acceptedByOthers + 1 >= capacity) {
                                   filledDate[0] = date;
                                 }
                                 return decided;
                               },
                               stretch -> SeatRule.holdsSeatOver(priorRows, stretch),
                               EventAttendeeResponse.ACCEPTED);
    } else {
      pieces = List.of(new SeatPiece(fromDate, null, SeatRule.decide(requested, null, 0, 0), null));
    }
    List<EventAttendee> written = writeSeriesPieces(seriesId, fromDate, identityId, priorRows, pieces, seriesDates, now);

    // One answer reported, at the date it is read at: the pieces are not
    // reported one by one
    ZonedDateTime readDate = fromDate;
    if (readDate == null && (pieces.size() > 1 || priorRows.size() > 1)) {
      readDate = seriesDates.readDate();
    }
    EventAttendee attendee = validAt(written, readDate).orElse(written.get(0));
    EventAttendee oldAttendee = validAt(priorRows, readDate).orElse(null);
    Utils.broadcastEvent(listenerService, Utils.POST_EVENT_RESPONSE_SAVED, oldAttendee, attendee);
    if (userResponseSent) {
      Utils.broadcastEvent(listenerService, Utils.POST_EVENT_RESPONSE_SENT, oldAttendee, attendee);
    }

    if (givesUpASeat(priorRows, pieces)) {
      promoteWaitingAttendees(seriesId);
    }
    if (filledDate[0] != null && series.getCapacityReachedDate() == null) {
      eventStorage.saveCapacityReachedDate(seriesId, now);
      series.setCapacityReachedDate(now);
      Utils.broadcastEvent(listenerService, Utils.POST_EVENT_CAPACITY_REACHED, seriesId, filledDate[0]);
    }
    ZonedDateTime firstWaitingOccurrenceId = pieces.stream()
                                                   .filter(piece -> piece.response() == EventAttendeeResponse.WAITING)
                                                   .map(SeatPiece::firstCountedDate)
                                                   .filter(Objects::nonNull)
                                                   .findFirst()
                                                   .orElse(null);
    return new EventResponseOutcome(attendee.getResponse(), firstWaitingOccurrenceId);
  }

  /**
   * Writes the pieces of an answer to a series, in ascending order, in place
   * of the attendee's rows they replace.
   *
   * @return the rows written, in the order of the pieces
   */
  private List<EventAttendee> writeSeriesPieces(long seriesId,
                                                ZonedDateTime fromDate,
                                                long identityId,
                                                List<EventAttendee> priorRows,
                                                List<SeatPiece> pieces,
                                                SeriesDates seriesDates,
                                                ZonedDateTime now) {
    // Dated against the rows as they were, before any of them is written
    List<ZonedDateTime> responseDates = pieces.stream()
                                              .map(piece -> SeatRule.pieceResponseDate(piece,
                                                                                       priorRows,
                                                                                       seriesDates::firstDate,
                                                                                       now))
                                              .toList();
    if (fromDate == null) {
      if (pieces.size() == 1 && priorRows.size() == 1) {
        // The lone row answers every date: its range goes
        EventAttendee row = priorRows.get(0).clone();
        row.setFromOccurrenceId(null);
        row.setUntilOccurrenceId(null);
        row.setResponse(pieces.get(0).response());
        row.setResponseDate(responseDates.get(0));
        attendeeStorage.saveEventAttendee(row, seriesId);
        return List.of(row);
      }
      priorRows.forEach(row -> attendeeStorage.removeEventAttendee(row.getId()));
    } else {
      for (EventAttendee priorRow : priorRows) {
        ZonedDateTime from = priorRow.getFromOccurrenceId();
        ZonedDateTime until = priorRow.getUntilOccurrenceId();
        if (from != null && !from.isBefore(fromDate)) {
          attendeeStorage.removeEventAttendee(priorRow.getId());
        } else if (until == null || until.isAfter(fromDate)) {
          EventAttendee capped = priorRow.clone();
          capped.setUntilOccurrenceId(fromDate);
          attendeeStorage.saveEventAttendee(capped, seriesId);
        }
      }
    }
    List<EventAttendee> written = new ArrayList<>();
    for (int i = 0; i < pieces.size(); i++) {
      SeatPiece piece = pieces.get(i);
      EventAttendee row = new EventAttendee(0,
                                            seriesId,
                                            identityId,
                                            piece.from(),
                                            piece.until(),
                                            piece.response(),
                                            responseDates.get(i));
      attendeeStorage.saveEventAttendee(row, seriesId);
      written.add(row);
    }
    return written;
  }

  /**
   * @return whether one of the attendee's rows held a seat on a date of the
   *         range answered that a piece no longer gives a seat on
   */
  private static boolean givesUpASeat(List<EventAttendee> priorRows, List<SeatPiece> pieces) {
    return priorRows.stream()
                    .filter(row -> row.getResponse() == EventAttendeeResponse.ACCEPTED)
                    .anyMatch(row -> pieces.stream()
                                           .filter(piece -> piece.response() != EventAttendeeResponse.ACCEPTED)
                                           .anyMatch(piece -> rangesOverlap(row.getFromOccurrenceId(),
                                                                            row.getUntilOccurrenceId(),
                                                                            piece.from(),
                                                                            piece.until())));
  }

  /**
   * @return whether two ranges of occurrence ids, from inclusive and until
   *         exclusive, null for no bound, share a date
   */
  private static boolean rangesOverlap(ZonedDateTime from, ZonedDateTime until, ZonedDateTime otherFrom, ZonedDateTime otherUntil) {
    return (until == null || otherFrom == null || otherFrom.isBefore(until))
        && (otherUntil == null || from == null || from.isBefore(otherUntil));
  }

  /**
   * @return {@link EventAttendeeResponse#ACCEPTED} when one of the rows holds
   *         a seat at the date, null otherwise
   */
  private static EventAttendeeResponse heldAt(List<EventAttendee> rows, ZonedDateTime date) {
    return rows.stream()
               .anyMatch(row -> row.getResponse() == EventAttendeeResponse.ACCEPTED && SeatRule.coversDate(row, date)) ? EventAttendeeResponse.ACCEPTED
                                                                                                                    : null;
  }

  /**
   * @return the first of the rows valid at the date, judged on their range
   *         alone; the first row when no date is given
   */
  private static Optional<EventAttendee> validAt(List<EventAttendee> rows, ZonedDateTime date) {
    return rows.stream().filter(row -> SeatRule.coversDate(row, date)).findFirst();
  }

  /**
   * Stores an answer of an attendee on the row of an event of no series, or
   * of a date of a series: a pool of one date. A Yes takes a seat when one is
   * free or the attendee already holds one, and waits otherwise. When the
   * answer leaves the attendee fewer seats than the rows it replaces held (a
   * seat given up, seated rows merged), the waiting list is promoted in the
   * same transaction while seats are free. An answer stored as a Yes that
   * leaves the pool full while it is not marked marks it (see
   * {@link #markCapacityReached(Event, ZonedDateTime)}). On a pool that
   * decides no seat, the answer is stored as with no limit, a place on the
   * waiting list asked becoming a Yes, and nobody is promoted.
   *
   * @param pool the stored row answered, as read under the lock
   * @param identityId the attendee's identity identifier
   * @param requested the answer given
   * @param userResponseSent whether the attendee sent it, reported as such
   * @param decidesSeat whether the answer decides a seat on the pool: not on
   *          the row of a date over that an answer to the series is copied to
   * @param decidedAt the instant the answer is decided at: whether a date is
   *          over is read at that instant by the seat and by the report alike
   * @return the attendee row stored
   */
  private EventAttendee saveEventAttendee(Event pool,
                                          long identityId,
                                          EventAttendeeResponse requested,
                                          boolean userResponseSent,
                                          boolean decidesSeat,
                                          ZonedDateTime decidedAt) {
    long eventId = pool.getId();
    EventAttendeeList eventAttendees = attendeeStorage.getEventAttendees(eventId, identityId);
    EventAttendee attendee = eventAttendees.getEventAttendee(identityId, null);
    EventAttendee oldAttendee = attendee == null ? null : attendee.clone();
    // An answer to the row replaces every row of the attendee: the seats they
    // hold and their place on the waiting list are read from all of those
    // rows, not only from the first one
    List<EventAttendee> replacedRows = eventAttendees.getEventAttendees(identityId);
    long seatsHeld = replacedRows.stream().filter(row -> row.getResponse() == EventAttendeeResponse.ACCEPTED).count();
    EventAttendeeResponse response = decidesSeat ? decideSeat(pool, identityId, requested, seatsHeld > 0)
                                                 : SeatRule.decide(requested, null, 0, 0);
    // The date changes only with the answer stored: when every replaced row
    // already holds it, the earliest of their dates is kept, a null one
    // included; now otherwise, a Yes again after a No on some dates included
    boolean answerUnchanged = !replacedRows.isEmpty()
        && replacedRows.stream().allMatch(row -> row.getResponse() == response);
    ZonedDateTime responseDate = answerUnchanged ? replacedRows.stream()
                                                               .min(Comparator.comparing(EventAttendee::getResponseDate,
                                                                                         Comparator.nullsFirst(Comparator.naturalOrder())))
                                                               .orElseThrow()
                                                               .getResponseDate()
                                                 : ZonedDateTime.now(ZoneOffset.UTC);
    if (replacedRows.size() > 1) {
      replacedRows.forEach(replacedRow -> attendeeStorage.removeEventAttendee(replacedRow.getId()));
      attendee = new EventAttendee(0, eventId, identityId, response);
    } else if (attendee == null) {
      attendee = new EventAttendee(0, eventId, identityId, response);
    } else {
      attendee.setResponse(response);
    }
    attendee.setResponseDate(responseDate);
    attendeeStorage.saveEventAttendee(attendee, eventId);

    // Broadcast technical event when saving any new response for a user
    Utils.broadcastEvent(listenerService, Utils.POST_EVENT_RESPONSE_SAVED, oldAttendee, attendee);

    // Broadcast user response to an event
    if (userResponseSent) {
      Utils.broadcastEvent(listenerService, Utils.POST_EVENT_RESPONSE_SENT, oldAttendee, attendee);
    }

    // Fewer seats held than before: a seat given up, or rows merged into one
    if (decidesSeat && seatsHeld > (response == EventAttendeeResponse.ACCEPTED ? 1 : 0)) {
      promoteWaitingAttendees(eventId);
    }
    if (decidesSeat && response == EventAttendeeResponse.ACCEPTED) {
      markCapacityReached(pool, decidedAt);
    }
    return attendee;
  }

  /**
   * Decides the answer stored for a requested one on a pool. Every Yes on a
   * pool of one date counts, its range ignored: a date's row gets the series'
   * rows valid at that date, and an answer on the pool keeps the row it
   * finds, so a row out of range is left from an older shape of the data —
   * a copy made before that filter, or a ranged answer kept when a series
   * became an event of no series — and counting it takes a seat away rather
   * than giving one more than the capacity.
   *
   * @param pool the stored row answered
   * @param identityId the attendee's identity identifier
   * @param requested the answer given
   * @param seatHeld whether one of the attendee's rows the answer replaces
   *          holds a seat
   * @return the answer to store
   */
  private EventAttendeeResponse decideSeat(Event pool,
                                           long identityId,
                                           EventAttendeeResponse requested,
                                           boolean seatHeld) {
    int capacity = SeatRule.capacityOf(pool);
    if (!SeatRule.asksForSeat(requested) || capacity <= 0) {
      return SeatRule.decide(requested, null, 0, 0);
    }
    int acceptedByOthers = SeatRule.acceptedAt(attendeeStorage.getSeatRows(pool.getId(), EventAttendeeResponse.ACCEPTED),
                                               null,
                                               identityId);
    return SeatRule.decide(requested, seatHeld ? EventAttendeeResponse.ACCEPTED : null, capacity, acceptedByOthers);
  }

  /**
   * Marks a pool of one date as having reached its capacity when an answer
   * stored as a Yes leaves it full while it is not marked, and queues the
   * event that reports it: the answer filling its last seat, or, after a
   * reset that cleared the mark while the answers it kept still fill the
   * pool, a Yes given again by an attendee holding one of those seats. A
   * promotion does not mark the pool it fills. The row of a date holding the
   * limit of its series is one of the dates the series carries (D21): it is
   * reported through the series' mark, once with them, at the date it is,
   * and not at all when a date of the series was reported before. A date
   * whose limit is its own, or that is cancelled or over, reports on its
   * own.
   *
   * @param pool the stored row answered
   * @param decidedAt the instant the answer was decided at, which tells
   *          whether the date is over as the seat decision read it
   */
  private void markCapacityReached(Event pool, ZonedDateTime decidedAt) {
    int capacity = SeatRule.capacityOf(pool);
    if (capacity <= 0 || pool.getCapacityReachedDate() != null) {
      return;
    }
    int accepted = SeatRule.acceptedAt(attendeeStorage.getSeatRows(pool.getId(), EventAttendeeResponse.ACCEPTED), null, 0);
    if (accepted < capacity) {
      return;
    }
    ZonedDateTime capacityReachedDate = ZonedDateTime.now(ZoneOffset.UTC);
    eventStorage.saveCapacityReachedDate(pool.getId(), capacityReachedDate);
    pool.setCapacityReachedDate(capacityReachedDate);
    Event reporter = reporterOf(pool, decidedAt);
    if (reporter != pool) {
      if (reporter.getCapacityReachedDate() != null) {
        return;
      }
      eventStorage.saveCapacityReachedDate(reporter.getId(), capacityReachedDate);
    }
    Utils.broadcastEvent(listenerService, Utils.POST_EVENT_CAPACITY_REACHED, reporter.getId(), occurrenceDateOf(pool));
  }

  /**
   * @param pool a full pool of one date
   * @param decidedAt the instant the answer was decided at
   * @return the row whose mark reports it: its series, when the pool is the
   *         row of a date the series reports (D21), confirmed, holding the
   *         series' limit and not over at that instant, read under the
   *         series' lock the step holds; else the pool itself
   */
  private Event reporterOf(Event pool, ZonedDateTime decidedAt) {
    if (pool.getParentId() <= 0 || pool.getStatus() != EventStatus.CONFIRMED) {
      return pool;
    }
    Event series = eventStorage.getEventById(pool.getParentId());
    return series != null && reportedByTheSeries(pool, series, decidedAt) ? series : pool;
  }

  /**
   * Whether a stored date is one its series reports (D21), the dates the
   * series' mark is written from: confirmed, holding the series' limit and
   * not over at the instant given, the one the caller read the dates at, so
   * that a seat decided on a date and the report of that date agree on
   * whether it is over.
   *
   * @param row the stored row of a date of a series
   * @param series its series, as stored
   * @param at the instant the date is over at, or not
   * @return whether the series' mark reports the row
   */
  private boolean reportedByTheSeries(Event row, Event series, ZonedDateTime at) {
    SeriesDates seriesDates = new SeriesDates(series, () -> eventStorage.getExceptionalOccurrences(series.getId()), at);
    return reportsThroughSeries(row, SeatRule.capacityOf(series)) && decidesSeat(row, seriesDates);
  }

  /**
   * The status and limit part of the predicate of the dates a series reports,
   * shared by every site that reads or writes the series' mark; a date over
   * is left out by {@link #decidesSeat(Event, SeriesDates)} at every site.
   *
   * @param row the stored row of a date of a series
   * @param seriesCapacity the series' limit
   * @return whether the date is one the series carries and reports (D21) by
   *         its status and limit: a confirmed date holding the series' limit
   */
  static boolean reportsThroughSeries(Event row, int seriesCapacity) {
    return row.getStatus() == EventStatus.CONFIRMED && SeatRule.capacityOf(row) == seriesCapacity;
  }

  /**
   * Writes the reached mark of a pool from the answers it holds, reporting
   * nothing: after its limit changed and the waiting list took the seats the
   * change gave, a limit written never reporting itself reached (D7). A pool
   * full under its limit is marked, so that no Yes given again on it reports
   * it; a pool with a seat free is cleared, so that the answer filling it
   * does. Runs inside the decide step that wrote the limit, on a series once
   * the dates it stores have decided their seats under it.
   *
   * @param poolId the identifier of the pool's event row, as stored
   */
  void writeCapacityReachedDate(long poolId) {
    Event pool = eventStorage.getEventById(poolId);
    boolean full = isFull(pool);
    if (full && pool.getCapacityReachedDate() == null) {
      eventStorage.saveCapacityReachedDate(poolId, ZonedDateTime.now(ZoneOffset.UTC));
    } else if (!full && pool.getCapacityReachedDate() != null) {
      eventStorage.saveCapacityReachedDate(poolId, null);
    }
  }

  /**
   * @param pool a stored row
   * @return whether the Yes answers reach its limit at one of the dates its
   *         mark reports: every row on the pool of one date; on a series, the
   *         rows valid at one of its counted dates, or the rows of a stored
   *         date it carries, confirmed, holding its limit and not over (D21)
   */
  private boolean isFull(Event pool) {
    int capacity = SeatRule.capacityOf(pool);
    if (capacity <= 0) {
      return false;
    }
    List<EventAttendee> acceptedRows = attendeeStorage.getSeatRows(pool.getId(), EventAttendeeResponse.ACCEPTED);
    if (pool.getRecurrence() == null) {
      return SeatRule.acceptedAt(acceptedRows, null, 0) >= capacity;
    }
    List<Event> rows = eventStorage.getExceptionalOccurrences(pool.getId());
    SeriesDates seriesDates = new SeriesDates(pool, () -> rows, ZonedDateTime.now(ZoneOffset.UTC));
    boolean aCountedDateFull = SeatRule.stretches(acceptedRows, null, null)
                                       .stream()
                                       .map(stretch -> seriesDates.firstCountedDate(stretch.from(), stretch.until()))
                                       .filter(Objects::nonNull)
                                       .anyMatch(date -> SeatRule.acceptedAt(acceptedRows, date, 0) >= capacity);
    return aCountedDateFull
        || rows.stream()
               .filter(row -> reportsThroughSeries(row, capacity) && decidesSeat(row, seriesDates))
               .anyMatch(row -> SeatRule.acceptedAt(attendeeStorage.getSeatRows(row.getId(), EventAttendeeResponse.ACCEPTED),
                                                    null,
                                                    0) >= capacity);
  }

  /**
   * After a stored date's write moved it into the dates its series reports
   * (D21): full under the series' limit, and not over, it marks the series
   * silently, a limit written never reporting itself reached (D7), so that
   * an answer then filling a date of the series reports nothing, as it would
   * had the series' limit been written. The series' mark is never cleared
   * here: its limit stands, and a mark standing after a seat was freed keeps
   * the series reported once while it does. Runs inside the decide step that
   * wrote the date, under the series' lock.
   *
   * @param series the series, as stored
   * @param row the date's row, as stored, holding the series' limit
   */
  void markSeriesReachedByDateJoined(Event series, Event row) {
    if (series.getCapacityReachedDate() != null) {
      return;
    }
    SeriesDates seriesDates = new SeriesDates(series,
                                              () -> eventStorage.getExceptionalOccurrences(series.getId()),
                                              ZonedDateTime.now(ZoneOffset.UTC));
    if (decidesSeat(row, seriesDates) && isFull(row)) {
      eventStorage.saveCapacityReachedDate(series.getId(), ZonedDateTime.now(ZoneOffset.UTC));
    }
  }

  /**
   * Gives the free seats of a pool to its waiting attendees, in the order they
   * asked: earliest answer date first, then lowest row id. Runs inside the
   * decide step that freed the seat. A promotion is stored as a Yes of now,
   * and reported as saved and as promoted, never as an answer the attendee
   * sent. On a series, the seats are given date by date (see
   * {@link #promoteSeriesWaitingAttendees(Event, List)}). The seats a decision
   * gives one attendee on one event are reported promoted once, whichever
   * pools of a series they were given on (see
   * {@link #reportPromotion(Event, PromotedShare)}); on a cancelled event or
   * date, they are not reported promoted.
   *
   * @param eventId the identifier of the pool's event row
   */
  void promoteWaitingAttendees(long eventId) {
    List<EventAttendee> waitingAttendees = attendeeStorage.getWaitingAttendees(eventId);
    if (waitingAttendees.isEmpty()) {
      return;
    }
    Event pool = eventStorage.getEventById(eventId);
    if (pool.getRecurrence() != null) {
      promoteSeriesWaitingAttendees(pool, waitingAttendees);
      return;
    }
    int capacity = SeatRule.capacityOf(pool);
    int accepted = SeatRule.acceptedAt(attendeeStorage.getSeatRows(eventId, EventAttendeeResponse.ACCEPTED), null, 0);
    for (EventAttendee waitingAttendee : waitingAttendees) {
      if (!SeatRule.hasFreeSeat(capacity, accepted)) {
        return;
      }
      EventAttendee oldAttendee = waitingAttendee.clone();
      waitingAttendee.setResponse(EventAttendeeResponse.ACCEPTED);
      waitingAttendee.setResponseDate(ZonedDateTime.now(ZoneOffset.UTC));
      attendeeStorage.saveEventAttendee(waitingAttendee, eventId);
      accepted++;
      Utils.broadcastEvent(listenerService, Utils.POST_EVENT_RESPONSE_SAVED, oldAttendee, waitingAttendee);
      reportPromotion(pool, new PromotedShare(oldAttendee, waitingAttendee, startOfDate(pool), 1));
    }
  }

  /**
   * Gives the free seats of the pool of a series to its waiting rows, date by
   * date (D17), in the order they asked, the Yes answers being read again
   * before each row. A waiting row is cut at the bounds of the stretches it
   * covers: the part with a seat at each counted date becomes a Yes of now,
   * the rest keeps waiting with its answer date, so that its place in the
   * order is kept. A stretch with no counted date takes the decision of its
   * neighbour; a row with none at all is left waiting, its dates having their
   * own row or being over. The row keeps its id for its first piece, whose
   * start does not move. Each row promoted is reported once as saved, its
   * first piece with a seat as stored, and once as promoted, from the first
   * date it got a seat for.
   *
   * @param series the row of the series, as read under the lock
   * @param waitingAttendees its waiting rows, in promotion order
   */
  private void promoteSeriesWaitingAttendees(Event series, List<EventAttendee> waitingAttendees) {
    long seriesId = series.getId();
    int capacity = SeatRule.capacityOf(series);
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    SeriesDates seriesDates = new SeriesDates(series, () -> eventStorage.getExceptionalOccurrences(seriesId), now);
    for (EventAttendee waitingAttendee : waitingAttendees) {
      List<EventAttendee> acceptedRows = attendeeStorage.getSeatRows(seriesId, EventAttendeeResponse.ACCEPTED);
      long identityId = waitingAttendee.getIdentityId();
      List<SeatPiece> pieces = SeatRule.pieces(SeatRule.stretches(acceptedRows,
                                                                  waitingAttendee.getFromOccurrenceId(),
                                                                  waitingAttendee.getUntilOccurrenceId()),
                                               stretch -> seriesDates.firstCountedDate(stretch.from(), stretch.until()),
                                               date -> SeatRule.hasFreeSeat(capacity,
                                                                            SeatRule.acceptedAt(acceptedRows,
                                                                                                date,
                                                                                                identityId)) ? EventAttendeeResponse.ACCEPTED
                                                                                                             : EventAttendeeResponse.WAITING,
                                               stretch -> false,
                                               EventAttendeeResponse.WAITING);
      if (pieces.stream().noneMatch(piece -> piece.response() == EventAttendeeResponse.ACCEPTED)) {
        continue;
      }
      EventAttendee oldAttendee = waitingAttendee.clone();
      EventAttendee promoted = null;
      ZonedDateTime firstPromotedDate = null;
      int promotedDates = 0;
      for (int i = 0; i < pieces.size(); i++) {
        SeatPiece piece = pieces.get(i);
        EventAttendee row = i == 0 ? waitingAttendee : new EventAttendee(0, seriesId, identityId, piece.from(), null, null, null);
        row.setUntilOccurrenceId(piece.until());
        row.setResponse(piece.response());
        row.setResponseDate(piece.response() == EventAttendeeResponse.ACCEPTED ? now : oldAttendee.getResponseDate());
        attendeeStorage.saveEventAttendee(row, seriesId);
        if (piece.response() == EventAttendeeResponse.ACCEPTED) {
          if (promoted == null) {
            promoted = row;
            firstPromotedDate = piece.firstCountedDate();
          }
          int pieceDates = seriesDates.countCountedDates(piece.from(), piece.until());
          promotedDates = pieceDates < 0 || promotedDates < 0 ? -1 : promotedDates + pieceDates;
        }
      }
      Utils.broadcastEvent(listenerService, Utils.POST_EVENT_RESPONSE_SAVED, oldAttendee, promoted);
      // The promotion is reported from the first date the attendee got a seat
      // for, which the row promoted does not carry when it starts with the
      // series, or at a date over or with a row of its own. A piece with a
      // seat always holds a counted date: a stretch with none takes the
      // decision of a counted neighbour and joins its run
      EventAttendee reported = promoted.clone();
      reported.setFromOccurrenceId(firstPromotedDate);
      reportPromotion(series, new PromotedShare(oldAttendee, reported, startOf(firstPromotedDate, series), promotedDates));
    }
  }

  /**
   * Reports a promotion once the transaction of the decide step is over,
   * merged with the other promotions the decision gives the same attendee on
   * the same event: to the attendee, the pool of a series and the pools of its
   * dates stored on a row of their own are one event. Seats given on a
   * cancelled event or date are not reported: they are no seat to announce
   * nor to show, and, merged, they would be the first date of the promotion,
   * which is not told on an event that is not confirmed, or one of its other
   * dates.
   *
   * @param pool the pool the seats were given on
   * @param share the seats given there
   */
  private void reportPromotion(Event pool, PromotedShare share) {
    if (pool.getStatus() == EventStatus.CANCELLED) {
      return;
    }
    long eventId = pool.getParentId() > 0 ? pool.getParentId() : pool.getId();
    List<Long> key = List.of(eventId, share.promoted().getIdentityId());
    if (!DecideContext.queueMerged(key, share, this::broadcastPromotion)) {
      broadcastPromotion(List.of(share));
    }
  }

  /**
   * Broadcasts the promotion of one attendee on one event, from the date
   * promoted that starts first, a date moved at its own start, the dates of
   * every pool counted after it.
   *
   * @param shares the seats given to the attendee, pool by pool
   */
  private void broadcastPromotion(List<PromotedShare> shares) {
    PromotedShare first = shares.stream()
                                .min(Comparator.comparing(PromotedShare::date, Comparator.nullsFirst(Comparator.naturalOrder())))
                                .orElseThrow();
    boolean openEnded = shares.stream().anyMatch(share -> share.dates() < 0);
    int otherDates = openEnded ? 0 : shares.stream().mapToInt(PromotedShare::dates).sum() - 1;
    Utils.broadcastEvent(listenerService,
                         Utils.POST_EVENT_ATTENDEE_PROMOTED,
                         first.before(),
                         new EventAttendeePromotion(first.promoted(), otherDates, openEnded));
  }

  private AgendaEventService getAgendaEventService() {
    if (agendaEventService == null) {
      agendaEventService = ExoContainerContext.getService(AgendaEventService.class);
    }
    return agendaEventService;
  }

  private ProfilePropertyService getProfilePropertyService() {
    if (profilePropertyService == null) {
      profilePropertyService = ExoContainerContext.getService(ProfilePropertyService.class);
    }
    return profilePropertyService;
  }

  private void dispatch(NotificationContext ctx, String... pluginId) {
    List<NotificationCommand> commands = new ArrayList<>(pluginId.length);
    for (String p : pluginId) {
      NotificationCommand command = ctx.makeCommand(PluginKey.key(p));
      if (command != null) {
        commands.add(command);
      }
    }

    if (commands.isEmpty() || DecideContext.queue(() -> execute(ctx, commands))) {
      return;
    }
    execute(ctx, commands);
  }

  private void execute(NotificationContext ctx, List<NotificationCommand> commands) {
    try {
      ctx.getNotificationExecutor().with(commands).execute(ctx);
    } catch (Exception e) {
      LOG.warn("Error sending invitation notifications", e);
    }
  }

  private void processSendingInvitation(Event event,
                                        List<EventAttendee> attendees,
                                        boolean sendInvitations,
                                        AgendaEventModification eventModifications) {
    if (sendInvitations) {
      if (event.getStatus() == EventStatus.CANCELLED) {
        eventModifications.setModificationTypes(Collections.singleton(AgendaEventModificationType.DELETED));
      }
      sendInvitations(event, attendees, eventModifications);
    }
  }

  /**
   * Creates the rows of the attendees the saved list adds. Another attendee's
   * answer carried by the list starts over; the saver's own Yes, or place on
   * the waiting list, takes a seat like any answer: stored as a Yes on a pool
   * with no limit, and returned to be decided once the list is stored on a
   * pool with one. Rows copied from a series (creatorUserId 0) keep the
   * answer and the answer date they had.
   *
   * @return the identity of the saver when their answer is to be decided
   */
  private List<Long> processAttendeesToCreate(Event pool,
                                              EventStatus eventStatus,
                                              List<EventAttendee> oldAttendees,
                                              List<EventAttendee> newAttendees,
                                              long creatorUserId,
                                              boolean resetResponses,
                                              Set<AgendaEventModificationType> eventModificationTypes) {
    long eventId = pool.getId();
    List<EventAttendee> attendeesToCreate =
                                          newAttendees.stream()
                                                      .filter(attendee -> oldAttendees.stream()
                                                                                      .noneMatch(newAttendee -> newAttendee.getIdentityId() == attendee.getIdentityId()))
                                                      .collect(Collectors.toList());

    List<Long> seatRequesters = new ArrayList<>();
    // Create new attendees
    for (EventAttendee eventAttendee : attendeesToCreate) {
      if (resetResponses || eventAttendee.getResponse() == null || eventStatus == EventStatus.TENTATIVE
          || isOtherAttendeeResponse(eventAttendee, creatorUserId)) {
        eventAttendee.setResponse(EventAttendeeResponse.NEEDS_ACTION);
      } else if (creatorUserId > 0 && SeatRule.asksForSeat(eventAttendee.getResponse())) {
        if (SeatRule.capacityOf(pool) > 0) {
          eventAttendee.setResponse(EventAttendeeResponse.NEEDS_ACTION);
          seatRequesters.add(eventAttendee.getIdentityId());
        } else {
          eventAttendee.setResponse(EventAttendeeResponse.ACCEPTED);
        }
      }
      if (creatorUserId > 0) {
        eventAttendee.setResponseDate(eventAttendee.getResponse() == EventAttendeeResponse.NEEDS_ACTION ? null
                                                                                                        : ZonedDateTime.now(ZoneOffset.UTC));
      }
      attendeeStorage.saveEventAttendee(eventAttendee, eventId);
    }
    if (!attendeesToCreate.isEmpty()) {
      eventModificationTypes.add(AgendaEventModificationType.ATTENDEE_ADDED);
    }
    return seatRequesters;
  }

  /**
   * A user can only answer an invitation for himself, thus a response carried by
   * the saved event can't be applied on another attendee than the user who
   * creates or updates the event.
   *
   * @param eventAttendee {@link EventAttendee} to create
   * @param creatorUserId {@link Identity} technical identifier of the user
   *          creating or updating the event, 0 when the attendee isn't saved on
   *          behalf of a user (exceptional occurrence creation for example,
   *          where responses are copied from the parent event)
   * @return true when the response is made on behalf of another attendee
   */
  private boolean isOtherAttendeeResponse(EventAttendee eventAttendee, long creatorUserId) {
    return creatorUserId > 0 && eventAttendee.getIdentityId() != creatorUserId;
  }

  /**
   * Removes the rows of the attendees the saved list drops.
   *
   * @return whether a removed row held a seat, which frees it
   */
  private boolean processAttendeesToDelete(List<EventAttendee> oldAttendees,
                                           List<EventAttendee> newAttendees,
                                           Set<AgendaEventModificationType> eventModificationTypes) {
    List<EventAttendee> attendeesToDelete =
                                          oldAttendees.stream()
                                                      .filter(attendee -> newAttendees.stream()
                                                                                      .noneMatch(newAttendee -> newAttendee.getIdentityId() == attendee.getIdentityId()))
                                                      .collect(Collectors.toList());

    // Delete attendees
    for (EventAttendee eventAttendee : attendeesToDelete) {
      attendeeStorage.removeEventAttendee(eventAttendee.getId());
    }
    if (!attendeesToDelete.isEmpty()) {
      eventModificationTypes.add(AgendaEventModificationType.ATTENDEE_DELETED);
    }
    return attendeesToDelete.stream().anyMatch(attendee -> attendee.getResponse() == EventAttendeeResponse.ACCEPTED);
  }
}
