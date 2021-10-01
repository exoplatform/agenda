// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.rest.model;

import java.io.Serializable;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.social.rest.entity.IdentityEntity;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventAttendeeEntity implements Serializable, Cloneable {

  private static final long     serialVersionUID = 4729314827604510766L;

  private long                  id;

  private IdentityEntity        identity;

  private EventAttendeeResponse response;

  @Override
  public EventAttendeeEntity clone() {// NOSONAR
    return new EventAttendeeEntity(id, identity, response);
  }
}
