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
public class EventPermission implements Cloneable, Serializable {

  private static final long serialVersionUID = -505066459639689152L;

  private boolean           canEdit;

  private boolean           attendee;

  @Override
  public EventPermission clone() { // NOSONAR
    return new EventPermission(canEdit, attendee);
  }
}
