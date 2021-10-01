// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.model;

import java.time.ZonedDateTime;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventOccurrence implements Cloneable {

  private ZonedDateTime id;

  private boolean       exceptional;

  private boolean       datesModified;

  public EventOccurrence(ZonedDateTime id) {
    this.id = id;
  }

  @Override
  public EventOccurrence clone() { // NOSONAR
    return new EventOccurrence(id, exceptional, datesModified);
  }
}
