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
package org.exoplatform.agenda.model;

import java.io.Serializable;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CalendarPermission implements Cloneable, Serializable {

  private static final long serialVersionUID = -505066459639689152L;

  private boolean           canCreate;

  private boolean           canEdit;

  private boolean           canInviteeEdit;

  /**
   * Whether the user may publish the calendar as a private link (EXO-90252):
   * the owner of a personal calendar, a member holding the manager role of the
   * space for a space calendar — never a super-manager who is not one.
   */
  private boolean           canPublish;

  /**
   * Builds the permissions a calendar grants, publishing left refused.
   *
   * @param canCreate whether the user may create events in the calendar
   * @param canEdit whether the user may edit the calendar
   * @param canInviteeEdit whether invitees may edit the calendar's events
   */
  public CalendarPermission(boolean canCreate, boolean canEdit, boolean canInviteeEdit) {
    this(canCreate, canEdit, canInviteeEdit, false);
  }

  /**
   * Copies the permissions, every field included. Goes through the
   * Lombok-generated all-args constructor (all four fields, in their
   * declared order) rather than the 3-arg constructor plus a setter: the
   * 3-arg constructor is the one that had its first two arguments swapped
   * here (EXO-90276) without a compiler error, because both are booleans —
   * the all-args form leaves no field for a future addition to silently
   * miss.
   *
   * @return a copy of these permissions
   */
  @Override
  public CalendarPermission clone() { // NOSONAR
    return new CalendarPermission(canCreate, canEdit, canInviteeEdit, canPublish);
  }
}
