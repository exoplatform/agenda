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
 * an eXo record (EXO-90357): what the owner's drawer lists under "Not shared in
 * eXo", read live from the channel.
 * <p>
 * A share granted to a user of this deployment can be recorded in eXo
 * ({@link #shareeIdentityId} set); the others — an address outside eXo, the
 * whole server, a published link — are listed for the owner's information and
 * can only be removed on the server, when the channel says they can.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalShare {

  /** The channel that listed it, as {@code CalendarShareChannelPlugin.id()} names it. */
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

  /** Whether the share grants more than reading, in which case it cannot be recorded as is. */
  private boolean readOnly;

}
