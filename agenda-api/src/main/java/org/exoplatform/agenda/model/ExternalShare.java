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
package org.exoplatform.agenda.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A share of a calendar that exists on a delivery channel's server but not as
 * an eXo record (EXO-90357), read live from the channel.
 * <p>
 * A share granted to a user of this deployment ({@link #shareeIdentityId} set)
 * whose {@link #access} is one eXo itself would write — {@code VIEW} or
 * {@code EDIT} (EXO-90378) — is recorded in eXo silently, as an adopted share
 * at that level, the moment the owner lists their shares; {@link #deliveryRef}
 * is what the record then carries. The others — an address outside eXo, the whole server, a
 * published link, a colleague holding more than reading — are listed to the
 * owner as access held outside eXo, and can only be removed on the server,
 * when the channel says they can.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalShare {

  /**
   * The channel that listed it: the plugin's
   * {@code CalendarShareChannelPlugin.id()}, or the qualified identifier it
   * stamps its deliveries with — {@code caldav:<serverId>} — so a removal
   * reaches the same server; agenda resolves the plugin by its {@code id()}
   * prefix.
   */
  private String  channelId;

  /**
   * The channel's own identifier of the grant, handed back to it to remove or
   * adopt the share.
   */
  private String  externalId;

  /**
   * What kind of grantee holds the share, in the channel's vocabulary —
   * {@code EXO_USER}, {@code OUTSIDE_EXO}, {@code EVERYONE}, {@code PUBLISHED_LINK}.
   */
  private String  kind;

  /** Identity identifier of the sharee when it is a user of this deployment, else 0. */
  private long    shareeIdentityId;

  /** How the channel names the grantee: a full name, an address, a label. */
  private String  displayName;

  /** Whether the share can be removed on the server from eXo. */
  private boolean removable;

  /** Whether the share grants reading only; a share granting more cannot be recorded as is. */
  private boolean readOnly;

  /**
   * What the grant amounts to in eXo's own vocabulary (EXO-90378):
   * {@code VIEW} for reading, {@code EDIT} for a grant of exactly the shape
   * eXo writes for an edit share, {@code MORE} for anything else — a
   * privilege set eXo would not write, and which it therefore never adopts.
   * Null from a channel that does not answer for it, which is read as
   * {@code MORE} when it is not read-only.
   */
  private String  access;

  /** The grantee's mail address, when the channel knows one; may be null. */
  private String  email;

  /**
   * What an eXo record adopting this share carries as its delivery reference
   * — for CalDAV the collection href, which the sharee's own listing of the
   * server is told apart by; null when the share cannot be recorded.
   */
  private String  deliveryRef;

  /**
   * A share without an address or a delivery reference.
   *
   * @param channelId the channel that listed it
   * @param externalId the channel's own identifier of the grant
   * @param kind what kind of grantee holds it
   * @param shareeIdentityId identity identifier of the sharee, else 0
   * @param displayName how the channel names the grantee
   * @param removable whether it can be removed on the server from eXo
   * @param readOnly whether it grants reading only
   */
  public ExternalShare(String channelId,
                       String externalId,
                       String kind,
                       long shareeIdentityId,
                       String displayName,
                       boolean removable,
                       boolean readOnly) {
    this(channelId, externalId, kind, shareeIdentityId, displayName, removable, readOnly, null, null, null);
  }

}
