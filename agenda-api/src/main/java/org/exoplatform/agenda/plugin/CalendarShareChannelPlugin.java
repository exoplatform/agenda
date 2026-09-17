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
   * Carries a share the owner just recorded, or shared again while it was
   * still undelivered.
   *
   * @param share the eXo record, never null
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
   * never listed twice. A read-only grant to a user of this deployment carries
   * its {@code deliveryRef}: agenda records it as an adopted share, silently
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

}
