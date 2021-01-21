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
package org.exoplatform.agenda.constant;

public enum EventAttendeeResponse {

  NEEDS_ACTION(EventAttendeeResponse.NEEDS_ACTION_VALUE),
  ACCEPTED(EventAttendeeResponse.ACCEPTED_VALUE),
  DECLINED(EventAttendeeResponse.DECLINED_VALUE),
  TENTATIVE(EventAttendeeResponse.TENTATIVE_VALUE);

  private static final String NEEDS_ACTION_VALUE = "NEEDS-ACTION";

  private static final String ACCEPTED_VALUE     = "ACCEPTED";

  private static final String DECLINED_VALUE     = "DECLINED";

  private static final String TENTATIVE_VALUE    = "TENTATIVE";

  private String              value;

  private EventAttendeeResponse(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static EventAttendeeResponse fromValue(String value) {
    switch (value) {
      case NEEDS_ACTION_VALUE:
        return NEEDS_ACTION;
      case ACCEPTED_VALUE:
        return ACCEPTED;
      case DECLINED_VALUE:
        return DECLINED;
      case TENTATIVE_VALUE:
        return TENTATIVE;
      default:
        return null;
    }
  }
}
