// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.model;

import java.time.ZonedDateTime;

import org.exoplatform.agenda.constant.EventAttendeeResponse;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventAttendee implements Cloneable {

  private long                  id;

  private long                  eventId;

  private long                  identityId;

  private ZonedDateTime         fromOccurrenceId;

  private ZonedDateTime         untilOccurrenceId;

  private EventAttendeeResponse response;

  public EventAttendee(long id, long identityId, EventAttendeeResponse response) {
    this.id = id;
    this.identityId = identityId;
    this.response = response;
  }

  public EventAttendee(long id, long eventId, long identityId, EventAttendeeResponse response) {
    this.id = id;
    this.eventId = eventId;
    this.identityId = identityId;
    this.response = response;
  }

  @Override
  public EventAttendee clone() { // NOSONAR
    return new EventAttendee(id,
                             eventId,
                             identityId,
                             fromOccurrenceId,
                             untilOccurrenceId,
                             response);
  }
}
