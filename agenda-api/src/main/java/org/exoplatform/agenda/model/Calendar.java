// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

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
