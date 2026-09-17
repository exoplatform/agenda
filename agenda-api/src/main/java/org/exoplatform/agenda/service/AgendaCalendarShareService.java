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

import java.util.List;
import java.util.Map;

import org.exoplatform.agenda.model.CalendarShare;
import org.exoplatform.agenda.model.ExternalShare;
import org.exoplatform.commons.exception.ObjectNotFoundException;

/**
 * Sharing a personal calendar, read-only, with colleagues (EXO-90357).
 * <p>
 * <b>The rules, all held here.</b> Only the owner of a personal calendar that
 * is not a subscription shares it; a sharee may read the calendar and its
 * events and nothing else, a private event as busy time only; a sharee may hide
 * the calendar from their agenda without the share being deleted. Space
 * calendars are not shareable in this version, nor is a share with a space or a
 * group.
 * <p>
 * <b>The status contract</b> of every method taking a user: the calendar must
 * exist ({@link ObjectNotFoundException}), the user must be its owner
 * ({@link IllegalAccessException}), then the arguments are validated
 * ({@link IllegalArgumentException} with a message code).
 * <p>
 * <b>Delivery channels</b> ({@code CalendarShareChannelPlugin}) are asked after
 * the record is written and never decide whether it exists.
 */
public interface AgendaCalendarShareService {

  /** Message code: the sharee is not a known user. */
  String SHAREE_UNKNOWN          = "agenda.share.shareeUnknown";

  /** Message code: the sharee is the owner. */
  String SHAREE_IS_OWNER         = "agenda.share.shareeIsOwner";

  /** Message code: the sharee's account is disabled or deleted. */
  String SHAREE_DISABLED         = "agenda.share.shareeDisabled";

  /** Message code: the calendar identifier is not positive. */
  String INVALID_CALENDAR        = "agenda.share.invalidCalendar";

  /** Message code: no channel carries this share, so nothing can be redelivered or removed there. */
  String NO_CHANNEL              = "agenda.share.noChannel";

  /** Message code: the sharee has no record on this calendar. */
  String SHARE_NOT_FOUND         = "agenda.share.notFound";

  /** Broadcast once a share is recorded; source the {@link CalendarShare}, data the owner identity id. */
  String CALENDAR_SHARED_EVENT   = "exo.agenda.calendar.shared";

  /** Broadcast once a share is deleted by its owner; source the {@link CalendarShare}, data the owner identity id. */
  String CALENDAR_UNSHARED_EVENT = "exo.agenda.calendar.unshared";

  /**
   * Shares a calendar with a colleague, then asks every delivery channel to
   * carry it. Sharing again with the same colleague changes nothing in eXo and
   * only retries a delivery that has not happened.
   *
   * @param calendarId technical identifier of the calendar
   * @param shareeUsername the colleague
   * @param ownerUsername the user sharing, who must own the calendar
   * @return the record, with {@code CalendarShare.getDeliveryWarning()} set when
   *         a channel should have carried it and could not
   * @throws ObjectNotFoundException when the calendar does not exist
   * @throws IllegalAccessException when the user does not own it, or it is a
   *           space calendar or a subscription
   * @throws IllegalArgumentException when the sharee is unknown, disabled, or
   *           the owner
   */
  CalendarShare share(long calendarId, String shareeUsername, String ownerUsername) throws ObjectNotFoundException,
                                                                                       IllegalAccessException;

  /**
   * Withdraws a share from the channel that carries it, then deletes the
   * record. The record is deleted even when the channel could not withdraw:
   * the grant then shows up among the external shares, with its own removal.
   * Unsharing a colleague who has no record succeeds silently.
   *
   * @param calendarId technical identifier of the calendar
   * @param shareeIdentityId identity identifier of the colleague
   * @param ownerUsername the user unsharing, who must own the calendar
   * @throws ObjectNotFoundException when the calendar does not exist
   * @throws IllegalAccessException when the user does not own it
   */
  void unshare(long calendarId, long shareeIdentityId, String ownerUsername) throws ObjectNotFoundException,
                                                                             IllegalAccessException;

  /**
   * Asks the channels again to carry a share they do not carry yet.
   *
   * @param calendarId technical identifier of the calendar
   * @param shareeIdentityId identity identifier of the colleague
   * @param ownerUsername the owner
   * @return the record, with a warning when the delivery failed again
   * @throws ObjectNotFoundException when the calendar or the share does not exist
   * @throws IllegalAccessException when the user does not own the calendar
   */
  CalendarShare redeliver(long calendarId, long shareeIdentityId, String ownerUsername) throws ObjectNotFoundException,
                                                                                         IllegalAccessException;

  /**
   * The colleagues a calendar is shared with, oldest share first.
   *
   * @param calendarId technical identifier of the calendar
   * @param ownerUsername the owner
   * @return the records, possibly empty
   * @throws ObjectNotFoundException when the calendar does not exist
   * @throws IllegalAccessException when the user does not own it
   */
  List<CalendarShare> getShares(long calendarId, String ownerUsername) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * The access a calendar's channels hold outside eXo, read live from every
   * channel. A read-only grant to a user of this deployment is not answered:
   * it is recorded, silently and without touching the server, as an adopted
   * share ({@code CalendarShareSource.ADOPTED}, delivered to the channel that
   * listed it), from then on a native share like any other — counted, seen by
   * the colleague under "Shared with me", revocable. Recording is idempotent
   * and notifies nobody: the colleague already had access. What is answered
   * is the rest: an address outside eXo, the whole server, a published link,
   * a colleague holding more than reading.
   *
   * @param calendarId technical identifier of the calendar
   * @param ownerUsername the owner
   * @return the access held outside eXo, possibly empty
   * @throws ObjectNotFoundException when the calendar does not exist
   * @throws IllegalAccessException when the user does not own it
   */
  List<ExternalShare> getExternalShares(long calendarId, String ownerUsername) throws ObjectNotFoundException,
                                                                                 IllegalAccessException;

  /**
   * Whether a channel writes copies of the owner's eXo meetings into the
   * calendar: what the drawer asks a confirmation for before sharing it
   * (EXO-90345), since a share then exposes every meeting the owner attends.
   *
   * @param calendarId technical identifier of the calendar
   * @param ownerUsername the owner
   * @return true when any channel says so
   * @throws ObjectNotFoundException when the calendar does not exist
   * @throws IllegalAccessException when the user does not own it
   */
  boolean holdsMeetingCopies(long calendarId, String ownerUsername) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Removes a share on a channel's server, at the owner's request.
   *
   * @param calendarId technical identifier of the calendar
   * @param channelId the channel that listed it
   * @param externalId the channel's identifier of the grant
   * @param ownerUsername the owner
   * @throws ObjectNotFoundException when the calendar does not exist, or no
   *           channel with that id is registered
   * @throws IllegalAccessException when the user does not own the calendar
   */
  void removeExternalShare(long calendarId, String channelId, String externalId, String ownerUsername) throws ObjectNotFoundException,
                                                                                                        IllegalAccessException;

  /**
   * How many colleagues each of the user's own calendars is shared with, from
   * one query; a calendar shared with nobody is absent.
   *
   * @param ownerUsername the owner
   * @return sharee counts by calendar identifier
   * @throws IllegalAccessException when the user has no usable identity
   */
  Map<Long, Long> countShareesByCalendar(String ownerUsername) throws IllegalAccessException;

  /**
   * The calendars shared with a user, newest share first, hidden ones included
   * (they say so). Shares whose calendar or owner is gone are left out.
   *
   * @param username the sharee
   * @return the records
   * @throws IllegalAccessException when the user has no usable identity
   */
  List<CalendarShare> getSharedWithMe(String username) throws IllegalAccessException;

  /**
   * Hides, or shows again, a calendar shared with the user. Never deletes the
   * share.
   *
   * @param calendarId technical identifier of the calendar
   * @param username the sharee
   * @param hidden whether to hide it
   * @throws ObjectNotFoundException when the calendar is not shared with the user
   * @throws IllegalAccessException when the user has no usable identity
   */
  void setHidden(long calendarId, String username, boolean hidden) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Whether a calendar is shared with an identity: the read primitive every
   * ACL path relies on. Cached per viewer; a revoke evicts the viewer's entry
   * so the refusal is immediate.
   *
   * @param calendarId technical identifier of the calendar
   * @param viewerIdentityId identity identifier of the reader
   * @return true when a record exists
   */
  boolean isSharedWith(long calendarId, long viewerIdentityId);

  /**
   * The identifiers of every calendar shared with an identity.
   *
   * @param viewerIdentityId identity identifier of the reader
   * @return the calendar identifiers, possibly empty, never null
   */
  List<Long> getSharedCalendarIds(long viewerIdentityId);

  /**
   * Deletes every share of a calendar, once the calendar is deleted. No
   * channel is asked: the calendar is gone.
   *
   * @param calendarId technical identifier of the calendar
   */
  void deleteShares(long calendarId);

  /**
   * Deletes every share a deleted user held, as sharee and as owner of the
   * shared calendars.
   *
   * @param identityId identity identifier of the deleted user
   */
  void deleteSharesOfUser(long identityId);

  /**
   * Forgets that a channel carries the shares of a user's calendars, once the
   * user disconnected from that channel: the eXo shares stay, the channel's
   * grants are left as the channel's truth.
   *
   * @param ownerIdentityId identity identifier of the owner
   * @param channelId the channel the owner left
   */
  void clearDelivery(long ownerIdentityId, String channelId);

}
