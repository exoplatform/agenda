// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.rest.model;

import java.io.Serializable;

import org.exoplatform.agenda.model.CalendarPermission;
import org.exoplatform.social.rest.entity.IdentityEntity;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CalendarEntity implements Serializable, Cloneable {

  private static final long  serialVersionUID = 2689301355435962774L;

  private long               id;

  private IdentityEntity     owner;

  private boolean            system;

  private String             title;

  private String             description;

  private String             created;

  private String             updated;

  private String             color;

  private CalendarPermission acl;

  @Override
  public CalendarEntity clone() {// NOSONAR
    return new CalendarEntity(id, owner, system, title, description, created, updated, color, acl == null ? null : acl.clone());
  }
}
