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
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.constant.EventVisibility;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.model.AgendaConnectorAccount;
import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.model.EventReminderParameter;
import org.exoplatform.agenda.model.HeldMailInvitation;
import org.exoplatform.agenda.model.LandedMailInvitation;
import org.exoplatform.agenda.model.MailInvitation;
import org.exoplatform.agenda.model.MailInvitationEvent;
import org.exoplatform.agenda.model.RemoteProvider;
import org.exoplatform.agenda.util.CalendarFeedParser;
import org.exoplatform.agenda.util.EventIcsBuilder;
import org.exoplatform.agenda.util.MailInvitationReader;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;

/**
 * Lands an invitation a user received by mail in their personal eXo calendar,
 * for a user whose calendar no calendar server holds (EXO-90866) — the
 * counterpart of caldav-integration's landing (EXO-90848), whose rules it
 * keeps. A user who connected a remote calendar account to agenda (Google,
 * Office 365, Exchange through agenda-connectors, or CalDAV) is not landed
 * for: their mail server has usually filed the invitation there, and the
 * connector shows or syncs that copy.
 *
 * <h2>What each click does</h2>
 * <p>
 * An invitation accepted or tentatively accepted, or added without an answer,
 * is created in the user's personal calendar with the user as its only
 * attendee, carrying the answer given; when the user holds it already, the
 * organiser's strictly newer revision (SEQUENCE) rewrites it, and the answer is
 * set on it. A decline creates nothing — a meeting turned down and never added
 * does not belong in the calendar — and is set on an event the user holds. A
 * cancellation removes the event the user holds, and does nothing for one they
 * never added. Nothing is ever mailed: the reader sent the answer, and agenda
 * answers here without broadcasting it.
 *
 * <h2>How a later message finds the event</h2>
 * <p>
 * The invitation's UID is kept with the event, in the event's own properties
 * (the {@code agendaEvent} metadata agenda already stores per event), under a
 * value naming the user too: two users holding the same meeting hold two events,
 * and the lookup reads only this user's. An event found that way is held only
 * when it is a series or single event the user created, in a calendar the user
 * owns — a property anybody able to edit an event can set is a hint, not a
 * proof. Beside it the SEQUENCE landed and the organiser's address, which is
 * what a later message is compared with. The remote-event mapping is
 * deliberately not used: it holds one row per user and event, which the
 * calendar connectors overwrite with their own identifiers.
 *
 * <h2>What is trusted</h2>
 * <p>
 * The object is the sender's ({@link MailInvitationReader} bounds and narrows
 * it). Only an invitation, a published event or a cancellation is landed — a
 * REPLY or a COUNTER speaks to an organiser, not to a calendar — and never a
 * message about one occurrence alone. An organiser is required where the
 * message speaks for one: an invitation, a cancellation, an answer. An event
 * the user holds is rewritten, answered or removed only on a message from its
 * own organiser (RFC 5546 section 3.2.2), never when the user organises it
 * themselves, and a cancellation older than what the user holds removes
 * nothing. One of this deployment's own meetings — its UID
 * {@code agenda-event-<id>@<this host>}, or its URL this deployment's agenda
 * link — is never landed: it is in agenda already, so adding it hands back its
 * page as already held when the user may read it and the message describes it,
 * and an answer or a cancellation carried by such a message is refused. The
 * only person acted for is the user who asked, in their own calendar; no
 * attendee line is resolved to anybody. The link handed back is agenda's own,
 * built from the platform's domain and the event's id.
 *
 * <h2>Limits</h2>
 * <p>
 * A series lands with its rule, its excluded dates and its overridden
 * occurrences; RDATE is not read. A newer revision rewrites the series, which
 * drops every exceptional occurrence agenda held for it, and the revision's own
 * exclusions and overrides are applied again. An exclusion or an override
 * agenda cannot place is skipped, the series kept. A floating time is read at
 * UTC.
 */
@Service
public class AgendaInvitationLandingService {

  /** The event property holding the user and the invitation's UID, looked up by. */
  public static final String       UID_PROPERTY       = "mailInvitationUid";

  /** The event property holding the SEQUENCE landed. */
  public static final String       SEQUENCE_PROPERTY  = "mailInvitationSequence";

  /** The event property holding the organiser's address, null for a published event. */
  public static final String       ORGANIZER_PROPERTY = "mailInvitationOrganizer";

  /**
   * The event property saying the event was added from a mail without an answer:
   * agenda then holds its creator's line as ACCEPTED, which nobody said.
   */
  public static final String       UNANSWERED_PROPERTY = "mailInvitationUnanswered";

  private static final Log         LOG                = ExoLogger.getLogger(AgendaInvitationLandingService.class);

  /** The methods a message may carry to be landed: an invitation, a published event, a cancellation, or none. */
  private static final Set<String> LANDABLE_METHODS   = Set.of("REQUEST", "PUBLISH", MailInvitation.CANCEL);

  /** The UID agenda mails for one of its own events ({@code Utils.icsUid}). */
  private static final Pattern     OWN_AGENDA_UID     = Pattern.compile("^agenda-event-(\\d{1,18})@(.+)$", Pattern.CASE_INSENSITIVE);

  /** The link agenda writes as the URL of one of its own events ({@code NotificationUtils.getEventURL}). */
  private static final Pattern     OWN_AGENDA_LINK    =
                                                   Pattern.compile("^https?://([^/\\s<>\"']+)/portal/[A-Za-z0-9_.-]+/agenda\\?eventId=(\\d{1,18})$",
                                                                   Pattern.CASE_INSENSITIVE);

  /** Most events read when looking the invitation up: one is expected. */
  private static final int         MAX_HELD_LOOKUP    = 20;

  private final IdentityManager              identityManager;

  private final AgendaCalendarService        agendaCalendarService;

  private final AgendaEventService           agendaEventService;

  private final AgendaEventAttendeeService   agendaEventAttendeeService;

  private final AgendaEventReminderService   agendaEventReminderService;

  private final AgendaEventConferenceService agendaEventConferenceService;

  private final AgendaRemoteEventService     agendaRemoteEventService;

  private final AgendaUserSettingsService    agendaUserSettingsService;

  private final MetadataService              metadataService;

  /**
   * Builds the service over agenda's own services and the metadata the event
   * properties are kept in.
   *
   * @param identityManager resolves the user
   * @param agendaCalendarService the user's personal calendar
   * @param agendaEventService creates, updates and removes the event
   * @param agendaEventAttendeeService sets the user's answer
   * @param agendaEventReminderService the reminders an update keeps
   * @param agendaEventConferenceService the conferences an update keeps
   * @param agendaRemoteEventService the remote mapping an update keeps
   * @param agendaUserSettingsService the user's default reminders
   * @param metadataService the event properties the invitation is found by
   */
  @Autowired
  public AgendaInvitationLandingService(IdentityManager identityManager,
                                        AgendaCalendarService agendaCalendarService,
                                        AgendaEventService agendaEventService,
                                        AgendaEventAttendeeService agendaEventAttendeeService,
                                        AgendaEventReminderService agendaEventReminderService,
                                        AgendaEventConferenceService agendaEventConferenceService,
                                        AgendaRemoteEventService agendaRemoteEventService,
                                        AgendaUserSettingsService agendaUserSettingsService,
                                        MetadataService metadataService) {
    this.identityManager = identityManager;
    this.agendaCalendarService = agendaCalendarService;
    this.agendaEventService = agendaEventService;
    this.agendaEventAttendeeService = agendaEventAttendeeService;
    this.agendaEventReminderService = agendaEventReminderService;
    this.agendaEventConferenceService = agendaEventConferenceService;
    this.agendaRemoteEventService = agendaRemoteEventService;
    this.agendaUserSettingsService = agendaUserSettingsService;
    this.metadataService = metadataService;
  }

  /**
   * Whether agenda holds a calendar for the user: an enabled user of the
   * platform, whose personal calendar agenda creates on first use, and who has
   * no remote calendar account connected to agenda. Whether another add-on
   * holds that user's calendar instead is not agenda's to read here; the
   * plugin asks them. No round trip to a server: an identity read (cached)
   * and a settings read (one small query for the remote providers).
   *
   * @param username the user's login
   * @return true when the invitation is agenda's to land
   */
  public boolean holdsCalendarFor(String username) {
    Identity identity = userIdentityOf(username);
    return identity != null && !connectsARemoteCalendar(Long.parseLong(identity.getId()));
  }

  /**
   * Lands the invitation in the user's personal calendar: creates or updates
   * the event, answers it, or removes it, as the message and the click say.
   *
   * @param invitation the invitation, the user and what they asked
   * @return what was done, or null when the user cannot have a calendar here
   *         or connected a remote calendar account,
   *         or when there was nothing to do — a decline, or a cancellation, of
   *         an event the user never added
   * @throws IllegalArgumentException when the message cannot be landed as it is
   *           — unreadable or over a cap, about another event than the one
   *           shown, about one occurrence only, a method that is not landed,
   *           naming no organiser where one is needed, naming one of this
   *           deployment's meetings for an answer or a cancellation, or
   *           touching an event of another organiser's
   * @throws IllegalStateException when the landing was attempted and agenda
   *           refused it
   */
  public LandedMailInvitation land(MailInvitation invitation) {
    Identity identity = userIdentityOf(invitation.username());
    if (identity == null) {
      return null;
    }
    long userIdentityId = Long.parseLong(identity.getId());
    if (connectsARemoteCalendar(userIdentityId)) {
      LOG.debug("The invitation of user {} is not landed in agenda: a remote calendar account is connected", userIdentityId);
      return null;
    }
    if (StringUtils.isNotBlank(invitation.recurrenceId())) {
      throw new IllegalArgumentException("The invitation is about one occurrence of a series, which is not landed");
    }
    MailInvitationEvent read = MailInvitationReader.read(invitation.icalendar(), invitation.uid());
    String method = methodOf(invitation, read);
    boolean cancellation = MailInvitation.CANCEL.equals(method);
    EventAttendeeResponse response = invitation.response() == EventAttendeeResponse.NEEDS_ACTION ? null : invitation.response();
    if (cancellation && response != null) {
      throw new IllegalArgumentException("A cancellation of " + read.uid() + " carries no answer");
    }
    boolean speaksForAnOrganiser = response != null || cancellation || "REQUEST".equals(method);
    if (speaksForAnOrganiser && read.organizer() == null) {
      throw new IllegalArgumentException("The invitation " + read.uid() + " names no organiser");
    }
    Long ownMeeting = ownEventOf(read);
    if (ownMeeting != null) {
      return heldInAgenda(ownMeeting, userIdentityId, response != null || cancellation, read);
    }
    Event held = heldEvent(userIdentityId, read.uid());
    List<String> addresses = addressesOf(identity, invitation.attendeeAddress());
    if (cancellation) {
      return held == null ? nothingHeld(read, userIdentityId, "cancellation") : remove(held, read, addresses, userIdentityId);
    }
    if (read.cancelled()) {
      throw new IllegalArgumentException("The invitation " + read.uid() + " describes a cancelled event, which is not added");
    }
    if (held == null) {
      if (response == EventAttendeeResponse.DECLINED) {
        return nothingHeld(read, userIdentityId, "decline");
      }
      Event created = create(read, response, userIdentityId);
      applyOccurrences(created.getId(), read, userIdentityId);
      answer(created.getId(), userIdentityId, response);
      LOG.debug("The invitation {} of user {} landed as event {}", read.uid(), userIdentityId, created.getId());
      return landed(created.getId());
    }
    boolean newer = read.sequence() > sequenceOf(held);
    String heldOrganizer = organizerOf(held);
    if (heldOrganizer != null || read.organizer() != null || newer) {
      // A published event held as it was added, added again, is the one case
      // with no organiser to compare: it changes nothing.
      refuseUnlessItsOrganiser(held, read, addresses);
    }
    EventAttendeeResponse heldResponse = responseOf(held.getId(), userIdentityId);
    if (newer) {
      update(held, read, userIdentityId);
      applyOccurrences(held.getId(), read, userIdentityId);
    }
    // The update may have reset the user's answer (agenda accepts on the
    // modifier's behalf when the dates move): what the user said stands.
    answer(held.getId(), userIdentityId, response == null ? heldResponse : response);
    LOG.debug("The invitation {} of user {} is event {}, {}", read.uid(), userIdentityId, held.getId(), newer ? "updated" : "kept");
    return landed(held.getId());
  }

  /**
   * The event the user's personal calendar holds for an invitation, for the mail
   * reader to say so when the invitation is opened (EXO-90873): found by the
   * same lookup a landing makes — this user's key, an event they created in a
   * calendar they own — with the answer agenda holds for them and the SEQUENCE
   * landed. Reads only, no round trip to a server. An event added without an
   * answer, whose line agenda still holds as its creator's ACCEPTED, is told
   * with no answer: an acceptance given later from the mail is then said by the
   * reader's own record of it, not by this.
   * <p>
   * Null wherever a landing would not act: a user agenda cannot serve or who
   * connected a remote calendar account, a message about one occurrence alone,
   * an event the user does not hold — one of this deployment's own meetings
   * included, which is never landed and so never found — an event cancelled
   * since, and an event the message's organiser is not the organiser of, or
   * that the user organises: the UID is the sender's, and only an event's own
   * organiser speaks for it.
   *
   * @param username the user's login
   * @param attendeeAddress the user's mailbox address, may be null
   * @param uid the invitation's UID
   * @param recurrenceId the occurrence the message is about, null for the
   *          series or a single event
   * @param organizer the message's organiser, null for a published event
   *          naming none
   * @return the event held, or null
   */
  public HeldMailInvitation held(String username, String attendeeAddress, String uid, String recurrenceId, String organizer) {
    if (StringUtils.isBlank(uid) || StringUtils.isNotBlank(recurrenceId)) {
      return null;
    }
    Identity identity = userIdentityOf(username);
    if (identity == null) {
      return null;
    }
    long userIdentityId = Long.parseLong(identity.getId());
    if (connectsARemoteCalendar(userIdentityId)) {
      return null;
    }
    Event held = heldEvent(userIdentityId, uid.trim());
    if (held == null || held.getStatus() == EventStatus.CANCELLED) {
      return null;
    }
    String heldOrganizer = organizerOf(held);
    if (heldOrganizer != null && addressesOf(identity, attendeeAddress).contains(heldOrganizer)
        || !StringUtils.equals(heldOrganizer, MailInvitationReader.bareAddress(organizer))) {
      LOG.debug("The event {} user {} holds for {} is not the message's organiser's to tell of", held.getId(), userIdentityId, uid);
      return null;
    }
    EventAttendeeResponse response = responseOf(held.getId(), userIdentityId);
    if (response == EventAttendeeResponse.ACCEPTED && held.getParameters() != null
        && Boolean.parseBoolean(held.getParameters().get(UNANSWERED_PROPERTY))) {
      // Added without an answer: the ACCEPTED is agenda's line for its creator,
      // not something the user said. A later Maybe or Decline is.
      response = null;
    }
    return new HeldMailInvitation(held.getId(), linkOf(held.getId()), response, sequenceOf(held));
  }

  /**
   * The user the landing acts for: an enabled, undeleted user of the platform.
   *
   * @param username their login
   * @return the identity, null when there is none to act for
   */
  private Identity userIdentityOf(String username) {
    if (StringUtils.isBlank(username)) {
      return null;
    }
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (identity == null || StringUtils.isBlank(identity.getId()) || identity.isDeleted() || !identity.isEnable()) {
      return null;
    }
    return identity;
  }

  /**
   * Whether the user has a remote calendar account connected to agenda —
   * Google, Office 365, Exchange through agenda-connectors, or a CalDAV
   * account — on a provider that is enabled, as agenda's own connector screen
   * counts it. Such a user's mail server has usually filed the invitation in
   * that remote calendar, which the connector shows or will sync (a CalDAV
   * account with no calendar bound yet included); an eXo copy beside it would
   * be the meeting twice. Fails closed: settings that cannot be read count as
   * a connected account, and nothing lands.
   *
   * @param userIdentityId identity of the user
   * @return true when an account is connected, or the settings cannot be read
   */
  private boolean connectsARemoteCalendar(long userIdentityId) {
    AgendaUserSettings settings;
    try {
      settings = agendaUserSettingsService.getAgendaUserSettings(userIdentityId);
    } catch (RuntimeException e) {
      LOG.debug("The agenda settings of user {} could not be read; agenda does not claim their calendar", userIdentityId, e);
      return true;
    }
    if (settings == null) {
      return true;
    }
    // Connected as agenda's own screen counts it: an account on a provider
    // that exists and is enabled. A provider an administrator switched off
    // shows nothing, so its account is no reason to step aside.
    Set<String> enabledProviders = new HashSet<>();
    if (settings.getRemoteProviders() != null) {
      for (RemoteProvider provider : settings.getRemoteProviders()) {
        if (provider != null && provider.isEnabled() && StringUtils.isNotBlank(provider.getName())) {
          enabledProviders.add(provider.getName());
        }
      }
    }
    List<AgendaConnectorAccount> accounts = settings.getConnectedConnectors();
    return accounts != null
        && accounts.stream().anyMatch(account -> account != null && enabledProviders.contains(account.getProviderName()));
  }

  /**
   * The method the message carries: the reader's, cross-checked with the
   * object's own.
   *
   * @param invitation the invitation as the reader read it
   * @param read the object as agenda read it
   * @return the method, null when neither names one
   * @throws IllegalArgumentException when the two disagree, or the method is
   *           not landed
   */
  private static String methodOf(MailInvitation invitation, MailInvitationEvent read) {
    String method = StringUtils.upperCase(StringUtils.trimToNull(invitation.method()), Locale.ROOT);
    if (method != null && read.method() != null && !method.equals(read.method())) {
      throw new IllegalArgumentException("The invitation " + read.uid() + " is read as a " + method + " and carries a "
          + read.method());
    }
    method = method == null ? read.method() : method;
    if (method != null && !LANDABLE_METHODS.contains(method)) {
      // A REPLY, a COUNTER, a REFRESH… speak to an organiser, not to a calendar.
      throw new IllegalArgumentException("A " + method + " is not landed in a calendar");
    }
    return method;
  }

  /**
   * The agenda event one of this deployment's own meetings is, when the
   * message names one: its UID is the one agenda mails for it, with this
   * deployment's host, or its URL is this deployment's agenda link. Another
   * deployment's meeting is an external event like any other.
   *
   * @param read the object
   * @return the event id, null when the message names none of this
   *         deployment's events
   * @throws IllegalArgumentException when the UID and the URL name two
   *           different events of this deployment
   */
  private static Long ownEventOf(MailInvitationEvent read) {
    Long byUid = null;
    Matcher uid = OWN_AGENDA_UID.matcher(read.uid());
    if (uid.matches() && uid.group(2).trim().equalsIgnoreCase(ownHost())) {
      byUid = Long.valueOf(uid.group(1));
    }
    Long byLink = null;
    String authority = ownAuthority();
    Matcher link = read.url() == null ? null : OWN_AGENDA_LINK.matcher(read.url());
    if (link != null && link.matches() && authority != null && authority.equalsIgnoreCase(link.group(1))) {
      byLink = Long.valueOf(link.group(2));
    }
    if (byUid != null && byLink != null && !byUid.equals(byLink)) {
      throw new IllegalArgumentException("The invitation names two different meetings of this deployment");
    }
    return byUid == null ? byLink : byUid;
  }

  /**
   * This deployment's host as agenda writes it into its UIDs.
   *
   * @return the host, null when the platform cannot be asked
   */
  private static String ownHost() {
    try {
      return Utils.icsUidHost();
    } catch (RuntimeException | LinkageError e) {
      LOG.debug("This deployment's own host could not be resolved; agenda's own UIDs go unrecognised", e);
      return null;
    }
  }

  /**
   * The authority — host and port — of this deployment's configured domain, the
   * one agenda builds its event links from.
   *
   * @return the authority, null when none is configured or it cannot be read
   */
  private static String ownAuthority() {
    String domain;
    try {
      domain = CommonsUtils.getCurrentDomain();
    } catch (RuntimeException | LinkageError e) {
      LOG.debug("This deployment's domain could not be read; agenda's own links go unrecognised", e);
      return null;
    }
    if (StringUtils.isBlank(domain)) {
      return null;
    }
    String rest = domain.trim().replaceFirst("^[A-Za-z][A-Za-z0-9+.-]*://", "");
    rest = StringUtils.substringBefore(rest, "/");
    return StringUtils.trimToNull(rest);
  }

  /**
   * One of this deployment's own meetings: it is in agenda already, and
   * agenda is where it is answered or cancelled. Adding it writes nothing and
   * hands back its page, as already held, when the user may read the event and
   * the message describes it — same title, start within a day, since the UID
   * and the URL are the sender's and either alone would link any event the user
   * may read under the sender's title.
   *
   * @param eventId the agenda event
   * @param userIdentityId identity of the user
   * @param answersOrCancels whether the message carries an answer or a
   *          cancellation
   * @param read the object
   * @return the event, already held, with its link
   * @throws IllegalArgumentException for an answer or a cancellation, when the
   *           user may not read the event, when it does not exist or was
   *           cancelled, or when the message does not describe it
   */
  private LandedMailInvitation heldInAgenda(long eventId, long userIdentityId, boolean answersOrCancels, MailInvitationEvent read) {
    if (answersOrCancels) {
      throw new IllegalArgumentException("The message names eXo meeting " + eventId + ", which is answered and cancelled in agenda");
    }
    Event event;
    try {
      event = agendaEventService.getEventById(eventId, null, userIdentityId);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException("The message names eXo meeting " + eventId + ", which the user may not read", e);
    }
    if (event == null) {
      throw new IllegalArgumentException("The message names eXo meeting " + eventId + ", which does not exist");
    }
    if (event.getStatus() == EventStatus.CANCELLED) {
      throw new IllegalArgumentException("The message names eXo meeting " + eventId + ", which was cancelled");
    }
    boolean sameTitle = StringUtils.equalsIgnoreCase(StringUtils.trimToEmpty(event.getSummary()),
                                                     StringUtils.trimToEmpty(read.summary()));
    boolean sameDay = event.getStart() != null
        && Duration.between(event.getStart().toInstant(), read.start().toInstant()).abs().compareTo(Duration.ofDays(1)) < 0;
    if (!sameTitle || !sameDay) {
      throw new IllegalArgumentException("The message names eXo meeting " + eventId + " and describes another event");
    }
    LOG.debug("The message of user {} names eXo meeting {}; it is in agenda already and nothing is written", userIdentityId, eventId);
    return new LandedMailInvitation(eventId, linkOf(eventId), false, true);
  }

  /**
   * The event the user holds for the invitation: a series or single event they
   * created, in a calendar they own, carrying the invitation's UID under their
   * name. Read with its properties.
   *
   * @param userIdentityId identity of the user
   * @param uid the invitation's UID
   * @return the event, null when the user holds none
   */
  private Event heldEvent(long userIdentityId, String uid) {
    List<MetadataItem> items =
                             metadataService.getMetadataItemsByMetadataNameAndTypeAndObjectAndMetadataItemProperty(Utils.EVENT_METADATA_NAME,
                                                                                                                   Utils.EVENT_METADATA_TYPE.getName(),
                                                                                                                   Utils.EVENT_METADATA_NAME,
                                                                                                                   UID_PROPERTY,
                                                                                                                   uidKey(userIdentityId, uid),
                                                                                                                   0,
                                                                                                                   MAX_HELD_LOOKUP);
    if (items == null) {
      return null;
    }
    for (MetadataItem item : items) {
      long eventId = NumberUtils.toLong(item.getObjectId());
      Event event = eventId <= 0 ? null : agendaEventService.getEventById(eventId);
      if (event == null || event.getParentId() > 0 || event.getCreatorId() != userIdentityId) {
        continue;
      }
      Calendar calendar = agendaCalendarService.getCalendarById(event.getCalendarId());
      if (calendar == null || calendar.isDeleted() || calendar.getOwnerId() != userIdentityId) {
        continue;
      }
      event.setParameters(item.getProperties() == null ? new HashMap<>() : new HashMap<>(item.getProperties()));
      return event;
    }
    return null;
  }

  /**
   * Nothing to do: the user never added the event the message is about.
   *
   * @param read the object
   * @param userIdentityId identity of the user
   * @param what the click, for the log
   * @return null
   */
  private static LandedMailInvitation nothingHeld(MailInvitationEvent read, long userIdentityId, String what) {
    LOG.debug("The {} of {} by user {} lands nothing: they hold no event for it", what, read.uid(), userIdentityId);
    return null;
  }

  /**
   * Creates the event in the user's personal calendar, with the user as its
   * attendee and their default reminders.
   *
   * @param read the object
   * @param response the answer given, null when none
   * @param userIdentityId identity of the user
   * @return the event created
   * @throws IllegalArgumentException when agenda refuses the event as the
   *           message describes it
   * @throws IllegalStateException when agenda refuses the user
   */
  private Event create(MailInvitationEvent read, EventAttendeeResponse response, long userIdentityId) {
    Calendar calendar = agendaCalendarService.getOrCreateCalendarByOwnerId(userIdentityId);
    if (calendar == null) {
      throw new IllegalStateException("No personal calendar could be created for user " + userIdentityId);
    }
    Event event = new Event();
    event.setCalendarId(calendar.getId());
    fill(event, read);
    event.setVisibility(EventVisibility.DEFAULT);
    Map<String, String> properties = propertiesOf(userIdentityId, read);
    if (response == null) {
      properties.put(UNANSWERED_PROPERTY, "true");
    }
    event.setParameters(properties);
    List<EventAttendee> attendees = new ArrayList<>();
    attendees.add(new EventAttendee(0, userIdentityId, response == null ? EventAttendeeResponse.ACCEPTED : response));
    try {
      return agendaEventService.createEvent(event,
                                            attendees,
                                            List.of(),
                                            defaultReminders(userIdentityId),
                                            null,
                                            null,
                                            false,
                                            userIdentityId);
    } catch (AgendaException e) {
      throw new IllegalArgumentException("Agenda refuses the invitation " + read.uid() + " as it is: " + e.getMessage(), e);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("User " + userIdentityId + " may not create an event in their own calendar", e);
    }
  }

  /**
   * Rewrites the event the user holds with the organiser's newer revision,
   * keeping what is the user's own — the attendees, the reminders, the
   * conferences, the visibility, the remote mapping a connector keeps.
   *
   * @param held the event, with its properties
   * @param read the newer revision
   * @param userIdentityId identity of the user
   * @throws IllegalArgumentException when agenda refuses the revision as it is
   * @throws IllegalStateException when agenda refuses the user
   */
  private void update(Event held, MailInvitationEvent read, long userIdentityId) {
    long eventId = held.getId();
    Map<String, String> properties = held.getParameters() == null ? new HashMap<>() : new HashMap<>(held.getParameters());
    properties.put(SEQUENCE_PROPERTY, String.valueOf(read.sequence()));
    fill(held, read);
    // Not stated keeps what the user chose (agenda resolves it from the stored event).
    held.setVisibility(null);
    held.setOccurrence(null);
    held.setParameters(properties);
    try {
      agendaEventService.updateEvent(held,
                                     agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees(),
                                     agendaEventConferenceService.getEventConferences(eventId),
                                     agendaEventReminderService.getEventReminders(eventId, userIdentityId),
                                     null,
                                     agendaRemoteEventService.findRemoteEvent(eventId, userIdentityId),
                                     false,
                                     userIdentityId);
    } catch (AgendaException e) {
      throw new IllegalArgumentException("Agenda refuses the revision of " + read.uid() + " as it is: " + e.getMessage(), e);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("User " + userIdentityId + " may not update event " + eventId, e);
    } catch (ObjectNotFoundException e) {
      throw new IllegalStateException("Event " + eventId + " of user " + userIdentityId + " is gone", e);
    }
  }

  /**
   * Removes the event the user holds, which its organiser called off.
   *
   * @param held the event, with its properties
   * @param read the cancellation
   * @param addresses the user's own addresses
   * @param userIdentityId identity of the user
   * @return what was removed
   * @throws IllegalArgumentException when the cancellation is not the event's
   *           organiser's, is older than what the user holds, or the user
   *           organises the event
   * @throws IllegalStateException when agenda refuses the removal
   */
  private LandedMailInvitation remove(Event held, MailInvitationEvent read, List<String> addresses, long userIdentityId) {
    refuseUnlessItsOrganiser(held, read, addresses);
    if (read.sequence() < sequenceOf(held)) {
      throw new IllegalArgumentException("The cancellation of " + read.uid() + " is older than the revision the user holds");
    }
    try {
      agendaEventService.deleteEventById(held.getId(), userIdentityId);
    } catch (ObjectNotFoundException e) {
      LOG.debug("Event {} was already gone from agenda", held.getId(), e);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("User " + userIdentityId + " may not remove event " + held.getId(), e);
    }
    LOG.debug("The cancelled invitation {} of user {} was removed from their calendar (event {})",
              read.uid(),
              userIdentityId,
              held.getId());
    return new LandedMailInvitation(held.getId(), null, true, false);
  }

  /**
   * Places the series' excluded and overridden occurrences, as exceptional
   * occurrences agenda holds: an exclusion, or an override the organiser
   * cancelled, as a cancelled occurrence; any other override with its own
   * times and text. One agenda cannot place is skipped: the series is worth
   * more than one of its occurrences.
   *
   * @param seriesId the series in agenda
   * @param read the object
   * @param userIdentityId identity of the user
   */
  private void applyOccurrences(long seriesId, MailInvitationEvent read, long userIdentityId) {
    if (read.recurrence() == null) {
      return;
    }
    for (ZonedDateTime excluded : read.excludedOccurrences()) {
      placeOccurrence(seriesId, excluded, null, userIdentityId);
    }
    for (MailInvitationEvent override : read.overrides()) {
      placeOccurrence(seriesId, override.occurrenceId(), override, userIdentityId);
    }
  }

  /**
   * Places one exceptional occurrence.
   *
   * @param seriesId the series in agenda
   * @param occurrenceId the occurrence, in the event's zone
   * @param override what replaces it, null to cancel it
   * @param userIdentityId identity of the user
   */
  private void placeOccurrence(long seriesId, ZonedDateTime occurrenceId, MailInvitationEvent override, long userIdentityId) {
    try {
      Event occurrence = agendaEventService.saveEventExceptionalOccurrence(seriesId, occurrenceId);
      if (occurrence == null) {
        return;
      }
      if (override == null || override.cancelled()) {
        // Cancelled, not deleted: deleting the exceptional occurrence removes
        // the exception, and the series covers that day again.
        occurrence.setStatus(EventStatus.CANCELLED);
      } else {
        fill(occurrence, override);
      }
      occurrence.setRecurrence(null);
      occurrence.setVisibility(null);
      long occurrenceEventId = occurrence.getId();
      agendaEventService.updateEvent(occurrence,
                                     agendaEventAttendeeService.getEventAttendees(occurrenceEventId).getEventAttendees(),
                                     agendaEventConferenceService.getEventConferences(occurrenceEventId),
                                     agendaEventReminderService.getEventReminders(occurrenceEventId, userIdentityId),
                                     null,
                                     null,
                                     false,
                                     userIdentityId);
    } catch (AgendaException | IllegalStateException | IllegalArgumentException e) {
      // The sender's dates: an occurrence the series does not have.
      LOG.debug("Occurrence {} of series {} could not be placed; the series is kept without it", occurrenceId, seriesId, e);
    } catch (Exception e) { // NOSONAR agenda declares several checked refusals here
      LOG.warn("Occurrence {} of series {} could not be placed; the series is kept without it", occurrenceId, seriesId, e);
    }
  }

  /**
   * Sets what the message says on an event: its text, its times, its
   * availability and its rule. Always confirmed — in agenda a tentative status
   * is a date poll — and never editable by an attendee.
   *
   * @param event the event to fill
   * @param read the component read
   */
  private static void fill(Event event, MailInvitationEvent read) {
    event.setSummary(StringUtils.defaultString(read.summary()));
    event.setDescription(CalendarFeedParser.descriptionMarkup(read.description()));
    event.setLocation(read.location());
    event.setAllDay(read.allDay());
    event.setTimeZoneId(read.timeZone());
    event.setStart(read.start());
    event.setEnd(read.end());
    event.setAvailability(read.availability());
    event.setStatus(EventStatus.CONFIRMED);
    if (read.occurrenceId() == null) {
      event.setRecurrence(read.recurrence() == null ? null : read.recurrence().clone());
    }
    event.setAllowAttendeeToUpdate(false);
    event.setAllowAttendeeToInvite(false);
  }

  /**
   * The properties kept with a landed event: the lookup key, the SEQUENCE
   * landed, the organiser.
   *
   * @param userIdentityId identity of the user
   * @param read the object
   * @return the properties
   */
  private static Map<String, String> propertiesOf(long userIdentityId, MailInvitationEvent read) {
    Map<String, String> properties = new HashMap<>();
    properties.put(UID_PROPERTY, uidKey(userIdentityId, read.uid()));
    properties.put(SEQUENCE_PROPERTY, String.valueOf(read.sequence()));
    if (read.organizer() != null) {
      properties.put(ORGANIZER_PROPERTY, read.organizer());
    }
    return properties;
  }

  /**
   * The value a landed event is looked up by: the user and the UID, so that a
   * lookup reads only that user's events whoever else holds the meeting.
   *
   * @param userIdentityId identity of the user
   * @param uid the invitation's UID
   * @return the key
   */
  static String uidKey(long userIdentityId, String uid) {
    return userIdentityId + "/" + uid;
  }

  /**
   * The SEQUENCE landed for a held event.
   *
   * @param held the event, with its properties
   * @return the SEQUENCE, 0 when none was kept
   */
  private static int sequenceOf(Event held) {
    String value = held.getParameters() == null ? null : held.getParameters().get(SEQUENCE_PROPERTY);
    try {
      return value == null ? 0 : Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * The organiser kept for a held event.
   *
   * @param held the event, with its properties
   * @return the address, null when none was kept
   */
  private static String organizerOf(Event held) {
    return held.getParameters() == null ? null : MailInvitationReader.bareAddress(held.getParameters().get(ORGANIZER_PROPERTY));
  }

  /**
   * Only an event's organiser rewrites, answers or removes it (RFC 5546
   * section 3.2.2): an event the user holds takes a message from the organiser
   * it was landed from and nobody else, and one the user organises takes none
   * — such a message was written by somebody who learnt the UID.
   *
   * @param held the event, with its properties
   * @param read the message's event
   * @param addresses the user's own addresses
   * @throws IllegalArgumentException when the message is not the event's
   *           organiser's
   */
  private static void refuseUnlessItsOrganiser(Event held, MailInvitationEvent read, List<String> addresses) {
    String heldOrganizer = organizerOf(held);
    if (heldOrganizer != null && addresses.contains(heldOrganizer)) {
      throw new IllegalArgumentException("The invitation " + read.uid() + " names an event the user organises; a mail does not touch it");
    }
    if (heldOrganizer == null || !heldOrganizer.equals(read.organizer())) {
      throw new IllegalArgumentException("The invitation " + read.uid() + " is not from the organiser of the event the user holds");
    }
  }

  /**
   * The user's own addresses: the mailbox the invitation was read in, and their
   * profile's.
   *
   * @param identity the user
   * @param attendeeAddress their mailbox address, may be null
   * @return the addresses, bare
   */
  private static List<String> addressesOf(Identity identity, String attendeeAddress) {
    List<String> addresses = new ArrayList<>();
    String mailbox = MailInvitationReader.bareAddress(attendeeAddress);
    if (mailbox != null) {
      addresses.add(mailbox);
    }
    String profile = identity.getProfile() == null ? null : MailInvitationReader.bareAddress(identity.getProfile().getEmail());
    if (profile != null && !addresses.contains(profile)) {
      addresses.add(profile);
    }
    return addresses;
  }

  /**
   * The user's answer to an event they hold.
   *
   * @param eventId the event
   * @param userIdentityId identity of the user
   * @return the answer, null when it cannot be read
   */
  private EventAttendeeResponse responseOf(long eventId, long userIdentityId) {
    try {
      return agendaEventAttendeeService.getEventResponse(eventId, null, userIdentityId);
    } catch (ObjectNotFoundException | IllegalAccessException e) {
      LOG.debug("The answer of user {} to event {} could not be read", userIdentityId, eventId, e);
      return null;
    }
  }

  /**
   * Sets the user's answer on the event, and through it on its occurrences,
   * without broadcasting it: the reader mailed it already, and the user is the
   * event's organiser in agenda, whom a "response sent" notification would
   * address.
   *
   * @param eventId the event
   * @param userIdentityId identity of the user
   * @param response the answer, null or NEEDS_ACTION for none
   * @throws IllegalStateException when agenda refuses the answer
   */
  private void answer(long eventId, long userIdentityId, EventAttendeeResponse response) {
    if (response == null || response == EventAttendeeResponse.NEEDS_ACTION) {
      return;
    }
    try {
      agendaEventAttendeeService.sendEventResponse(eventId, userIdentityId, response, false);
    } catch (ObjectNotFoundException | IllegalAccessException e) {
      throw new IllegalStateException("The answer of user " + userIdentityId + " to event " + eventId + " was refused by agenda", e);
    }
  }

  /**
   * The user's default reminders, as they would get them on an event they
   * create in agenda.
   *
   * @param userIdentityId identity of the user
   * @return the reminders, empty when none
   */
  private List<EventReminder> defaultReminders(long userIdentityId) {
    AgendaUserSettings settings = agendaUserSettingsService.getAgendaUserSettings(userIdentityId);
    List<EventReminderParameter> parameters = settings == null ? null : settings.getReminders();
    if (parameters == null || parameters.isEmpty()) {
      return new ArrayList<>();
    }
    List<EventReminder> reminders = new ArrayList<>();
    for (EventReminderParameter parameter : parameters) {
      reminders.add(new EventReminder(0, 0, userIdentityId, parameter.getBefore(), parameter.getBeforePeriodType()));
    }
    return reminders;
  }

  /**
   * What a landing tells the reader.
   *
   * @param eventId the event
   * @return the event and its link
   */
  private static LandedMailInvitation landed(long eventId) {
    return new LandedMailInvitation(eventId, linkOf(eventId), false, false);
  }

  /**
   * Agenda's own page for the event, built from the platform's configured
   * domain and the event's id — never from anything in the message.
   *
   * @param eventId the agenda event
   * @return the link, or null when none can be built
   */
  private static String linkOf(long eventId) {
    return EventIcsBuilder.eventUrl(eventId);
  }
}
