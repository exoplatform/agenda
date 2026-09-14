/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.entity;

import java.io.Serializable;

import io.meeds.common.persistence.PortableSequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One occurrence a subscription imported (EXO-90278): the agenda event holding
 * it ({@code EVENT_ID}, unique) and its identity in the feed ({@code EVENT_KEY},
 * unique, derived from the subscription, the UID and the recurrence instant).
 */
@Entity(name = "AgendaCalendarSubscriptionEvent")
@Table(name = "EXO_AGENDA_SUBSCRIPTION_EVENT")
@Getter
@Setter
public class CalendarSubscriptionEventEntity implements Serializable {

  private static final long serialVersionUID = 2893349130650813572L;

  /** Technical identifier of the row. */
  @Id
  @PortableSequence(name = "SEQ_AGENDA_SUBSCRIPTION_EVT_ID")
  @Column(name = "SUBSCRIPTION_EVENT_ID")
  private Long              id;

  /** The subscription that imported the occurrence. */
  @Column(name = "SUBSCRIPTION_ID", nullable = false)
  private long              subscriptionId;

  /** The agenda event holding the occurrence; unique. */
  @Column(name = "EVENT_ID", nullable = false)
  private long              eventId;

  /** Identity of the occurrence in the feed; unique. */
  @Column(name = "EVENT_KEY", nullable = false)
  private String            eventKey;

  /** SHA-256 of the imported fields. */
  @Column(name = "CONTENT_HASH", nullable = false)
  private String            contentHash;

}
