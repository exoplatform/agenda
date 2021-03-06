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

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Calendar implements Cloneable {

  private long               id;

  private long               ownerId;

  private boolean            system;

  private boolean            deleted;

  private String             title;

  private String             description;

  private String             created;

  private String             updated;

  private String             color;

  private CalendarPermission acl;

  public Calendar(long id,
                  long ownerId,
                  boolean system,
                  String title,
                  String description,
                  String created,
                  String updated,
                  String color,
                  CalendarPermission acl) {
    this.id = id;
    this.ownerId = ownerId;
    this.system = system;
    this.title = title;
    this.description = description;
    this.created = created;
    this.updated = updated;
    this.color = color;
    this.acl = acl;
  }

  public Calendar clone() { // NOSONAR
    return new Calendar(id, ownerId, system, deleted, title, description, created, updated, color, acl);
  }
}
