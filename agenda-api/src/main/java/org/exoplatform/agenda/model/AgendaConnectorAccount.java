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
 * One remote account a user connected to their agenda, identified by the
 * connector (remote provider) it belongs to. A user may hold several of these
 * at the same time — typically one CalDAV account backing "My Calendars" plus
 * one or more remote accounts (Google, Office 365) — at most one per provider.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgendaConnectorAccount implements Cloneable {

  /**
   * Name of the remote provider (connector) this account is held on, as
   * declared by {@link RemoteProvider#getName()}.
   */
  private String  providerName;

  /**
   * Identifier of the user on the remote provider (generally an email
   * address).
   */
  private String  remoteUserId;

  /**
   * Whether this account receives copies of the meetings the user accepts or
   * organises. True by default: connecting an account opts it in, and each
   * account's settings row carries the switch to opt it out.
   */
  private boolean pushEnabled = true;

  /**
   * Clones this account into an independent instance, so that mutating the
   * copy (e.g. from a cloned {@link AgendaUserSettings}) never alters the
   * original.
   *
   * @return a field-by-field copy of this account
   */
  @Override
  public AgendaConnectorAccount clone() { // NOSONAR
    return new AgendaConnectorAccount(providerName, remoteUserId, pushEnabled);
  }

}
