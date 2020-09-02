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
public class EventConference implements Cloneable, Serializable {

  private static final long serialVersionUID = -1220341038848536226L;

  private long              id;

  /**
   * Made transient to avoid returning this in REST Response
   */
  private transient long    eventId;

  private String            type;

  private String            uri;

  private String            phone;

  private String            accessCode;

  private String            description;

  @Override
  public EventConference clone() { // NOSONAR
    return new EventConference(id, eventId, type, uri, phone, accessCode, description);
  }
}
