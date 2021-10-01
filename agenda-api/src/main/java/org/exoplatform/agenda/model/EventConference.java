// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.model;

import java.io.Serializable;

import lombok.*;
import lombok.EqualsAndHashCode.Exclude;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventConference implements Cloneable, Serializable {

  private static final long serialVersionUID = -1220341038848536226L;

  @Exclude
  private long              id;

  /**
   * Made transient to avoid returning this in REST Response
   */
  private transient long    eventId;

  private String            type;

  private String            url;

  private String            phone;

  private String            accessCode;

  private String            description;

  @Override
  public EventConference clone() { // NOSONAR
    return new EventConference(id, eventId, type, url, phone, accessCode, description);
  }
}
