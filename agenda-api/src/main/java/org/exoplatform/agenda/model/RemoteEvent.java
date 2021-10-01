// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.model;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RemoteEvent implements Cloneable {

  private long   id;

  private long   eventId;

  private long   identityId;

  /**
   * External Event technical identifier if imported from external Store. This
   * can be used to identify calendar event if re-importing events from remote
   * provider.
   */
  private String remoteId;

  private long   remoteProviderId;

  /**
   * Configured Identifier of Remote Calendar identifier, for example:
   * "agenda.googleCalendar" for Google Calendar and "agenda.office365Calendar"
   * for Office 365 Calendar.
   */
  private String remoteProviderName;

  @Override
  protected RemoteEvent clone() { // NOSONAR
    return new RemoteEvent(id, eventId, identityId, remoteId, remoteProviderId, remoteProviderName);
  }
}
