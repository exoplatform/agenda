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
package org.exoplatform.agenda.plugin;

import java.util.List;

import org.exoplatform.agenda.model.CalendarShare;
import org.exoplatform.agenda.model.ChannelDelivery;
import org.exoplatform.agenda.model.ChannelShares;
import org.exoplatform.agenda.model.ExternalShare;

/**
 * A channel that can also carry a calendar share outside eXo — the CalDAV
 * add-on granting the colleague's principal read access to the collection
 * that mirrors the calendar (EXO-90357).
 *
 * <h2>Agenda owns the share; a channel delivers it</h2>
 *
 * <p>
 * Sharing a calendar with a colleague is an eXo fact, recorded by agenda and
 * enforced by agenda's own ACL, for every owner — including one who never
 * connected a CalDAV account. A channel is asked, after the record is written,
 * to carry the same share where it can; its answer never decides whether the
 * share exists. A channel that fails leaves the record standing, undelivered:
 * agenda logs the failure at WARN and tells the owner nothing, and nothing is
 * retried on its own. A channel that says the share is none of its business
 * leaves the record eXo-only. A grant a channel holds for a colleague of this
 * deployment that agenda has no record of is recorded silently, as an adopted
 * share, the moment the owner lists their shares.
 *
 * <h2>How it is registered</h2>
 *
 * <p>
 * As a Spring {@code @Service} bean, non-final, in the contributing add-on's
 * own context: agenda collects every bean of this type at the moment it asks,
 * so an add-on whose WAR boots after agenda's is found just the same, and a
 * deployment without the add-on simply has none. Same idiom as
 * {@link RemoteEventCopyPlugin}.
 *
 * <h2>No business logic here</h2>
 *
 * <p>
 * An implementation transfers the call to its own service layer and answers;
 * it decides nothing about who may share what. It may throw: agenda reads an
 * exception as a {@link ChannelDelivery.Status#FAILED} delivery, or as a
 * withdrawal that did not happen, and carries on with the eXo record.
 */
public interface CalendarShareChannelPlugin {

  /**
   * The channel's identifier — {@code caldav}. Stable across restarts. A
   * delivery may qualify it with the server that holds the grant,
   * {@code caldav:<serverId>}, on the record's {@code deliveredTo}: agenda
   * resolves the channel by that prefix when it withdraws the share.
   *
   * @return the identifier, never blank
   */
  String id();

  /**
   * Makes this channel's grant match a share record: carries a share the owner
   * just recorded, shared again while it was still undelivered, or levelled
   * (EXO-90378).
   * <p>
   * <b>Idempotent, and reconciling.</b> The record's
   * {@code CalendarShare.getLevel()} is what the channel must end up holding:
   * {@code EDIT} over an existing read grant widens it, {@code VIEW} over an
   * edit grant narrows it, and either over a grant that already matches writes
   * nothing. A channel that can carry the share but not at that level answers
   * {@link ChannelDelivery#delivered(String, String, org.exoplatform.agenda.constant.CalendarShareLevel)}
   * naming the level it does hold, rather than failing: the share still stands
   * in eXo at the level the owner chose, and that is what decides every right
   * inside eXo.
   *
   * @param share the eXo record, never null, carrying the level to match
   * @param ownerUsername the owner of the calendar
   * @return what happened, never null
   */
  ChannelDelivery deliver(CalendarShare share, String ownerUsername);

  /**
   * Withdraws a share this channel carried, before agenda deletes the record.
   * Called only for a record whose {@code deliveredTo} is this channel's id.
   *
   * @param share the eXo record, never null
   * @param ownerUsername the owner of the calendar
   * @return true when the grant is gone on the channel's side, false when it
   *         could not be removed — the record is deleted all the same, and the
   *         grant then shows up under the external shares
   */
  boolean withdraw(CalendarShare share, String ownerUsername);

  /**
   * The shares of a calendar that exist on this channel's server without an
   * eXo record, read live. The channel leaves out every grantee agenda already
   * holds a record for — {@code recordedShareeIds} — so a delivered share is
   * never listed twice. A grant to a user of this deployment whose shape eXo
   * itself writes — {@code ExternalShare.access} {@code VIEW} or {@code EDIT}
   * (EXO-90378) — carries its {@code deliveryRef}: agenda records it as an
   * adopted share at that level, silently
   * and without touching the server, the moment the owner lists their shares.
   * Every other grant is listed to the owner as access held outside eXo.
   *
   * @param calendarId technical identifier of the calendar
   * @param ownerUsername the owner of the calendar
   * @param recordedShareeIds identity identifiers of the colleagues agenda
   *          already holds a record for, never null
   * @return the external shares, empty when the calendar is not on this
   *         channel or the owner has no account there
   */
  List<ExternalShare> listExternalShares(long calendarId, String ownerUsername, List<Long> recordedShareeIds);

  /**
   * Removes an external share on the server, at the owner's request.
   *
   * @param calendarId technical identifier of the calendar
   * @param externalId the channel's identifier of the grant
   * @param ownerUsername the owner of the calendar
   * @return true when removed
   */
  boolean removeExternalShare(long calendarId, String externalId, String ownerUsername);

  /**
   * Whether this channel writes copies of the owner's eXo meetings into the
   * calendar — the mirror calendar of a connected account. Sharing such a
   * calendar exposes the titles, descriptions and spaces of every meeting the
   * owner attends, private spaces included, so the drawer asks once before
   * sharing it (EXO-90345). A channel that copies nothing answers false, the
   * default.
   *
   * @param calendarId technical identifier of the calendar
   * @param ownerUsername the owner of the calendar
   * @return true when the calendar receives meeting copies from this channel
   */
  default boolean holdsMeetingCopies(long calendarId, String ownerUsername) {
    return false;
  }

  /**
   * Everything this channel has to say about a calendar when the owner opens
   * the Share drawer (EXO-90385): {@link #listExternalShares} and
   * {@link #holdsMeetingCopies}, in one ask.
   *
   * <p>
   * <b>Why agenda asks this one rather than the two.</b>
   * The two answers come out of the same conversation with the channel's
   * server, and a channel asked for them separately holds that conversation
   * twice — resolving the same collection, asking the same account. Against a
   * remote CalDAV server that is seconds of pure latency on every opening of
   * the drawer. A channel that can answer both from one read <b>overrides this
   * method</b> and does so; the default below keeps the two calls, so a channel
   * written before EXO-90385 needs no change and behaves as it did.
   *
   * <p>
   * The default asks the two <b>independently</b>, each under its own guard,
   * because agenda now has a single call in which to lose both: before
   * EXO-90385 it ran two loops over the channels with a guard each, so a
   * channel whose list read threw was still asked for the flag and could still
   * raise the warning. Folding the two into one call without the two guards
   * would silence the warning for that channel — the expensive direction, per
   * the tolerance below.
   *
   * <p>
   * Same contract as the two methods it stands for, including their
   * tolerances: the external shares are those the channel's server holds that
   * agenda has no record of, {@code recordedShareeIds} left out, and the flag
   * is what {@link #holdsMeetingCopies} means. A channel that cannot read the
   * server answers an empty list, and still answers the flag as best it can —
   * a missed warning exposes the owner's meetings while a false one costs a
   * click.
   *
   * @param calendarId technical identifier of the calendar
   * @param ownerUsername the owner of the calendar
   * @param recordedShareeIds identity identifiers of the colleagues agenda
   *          already holds a record for, never null
   * @return the external shares and the meeting-copies flag, never null
   */
  default ChannelShares listShares(long calendarId, String ownerUsername, List<Long> recordedShareeIds) {
    List<ExternalShare> listed;
    try {
      listed = listExternalShares(calendarId, ownerUsername, recordedShareeIds);
    } catch (RuntimeException | LinkageError e) {
      // The flag is still owed: an unreadable access list must not silently
      // turn the warning off, which is what one shared guard would have done
      listed = List.of();
    }
    try {
      return new ChannelShares(listed, holdsMeetingCopies(calendarId, ownerUsername));
    } catch (RuntimeException | LinkageError e) {
      return new ChannelShares(listed, false);
    }
  }

}
