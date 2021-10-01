// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

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

  @Override
  public CalendarPermission clone() { // NOSONAR
    return new CalendarPermission(canEdit, canCreate, canInviteeEdit);
  }
}
