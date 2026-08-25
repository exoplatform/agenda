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
