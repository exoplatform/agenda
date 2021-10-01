// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.rest.model;

import java.io.Serializable;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventReminderEntity implements Serializable, Cloneable {

  private static final long serialVersionUID = 6943741617810854720L;

  private long              id;

  private int               before;

  private String            beforePeriodType;

  @Override
  public EventReminderEntity clone() {// NOSONAR
    return new EventReminderEntity(id, before, beforePeriodType);
  }
}
