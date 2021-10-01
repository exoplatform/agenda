// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.*;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.commons.api.persistence.ExoEntity;

@Entity(name = "AgendaEventAttendee")
@ExoEntity
@Table(name = "EXO_AGENDA_ATTENDEE")
@NamedQueries(
  {
      @NamedQuery(
          name = "AgendaEventAttendee.deleteCalendarAttendees",
          query = "DELETE FROM AgendaEventAttendee a WHERE a.event.id IN (SELECT evt.id FROM AgendaEvent evt WHERE evt.calendar.id = :calendarId)"
      ),
      @NamedQuery(
          name = "AgendaEventAttendee.deleteEventAttendees",
          query = "DELETE FROM AgendaEventAttendee a WHERE a.event.id = :eventId"
      ),
      @NamedQuery(
          name = "AgendaEventAttendee.getEventAttendee",
          query = "SELECT a FROM AgendaEventAttendee a WHERE a.event.id = :eventId AND  a.identityId = :identityId"
      ),
      @NamedQuery(
          name = "AgendaEventAttendee.getEventAttendeesByEventId",
          query = "SELECT a FROM AgendaEventAttendee a"
              + "  WHERE a.event.id = :eventId"
      ),
      @NamedQuery(
          name = "AgendaEventAttendee.getEventAttendeesByEventIdAndByResponses",
          query = "SELECT a FROM AgendaEventAttendee a"
              + "  WHERE a.event.id = :eventId"
              + "  AND a.response in (:responses)"
      ),
  }
)
public class EventAttendeeEntity implements Serializable {

  private static final long     serialVersionUID = 8633143729031653190L;

  @Id
  @SequenceGenerator(name = "SEQ_AGENDA_EVENT_ATTENDEE_ID", sequenceName = "SEQ_AGENDA_EVENT_ATTENDEE_ID")
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_AGENDA_EVENT_ATTENDEE_ID")
  @Column(name = "EVENT_ATTENDEE_ID")
  private Long                  id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "EVENT_ID", referencedColumnName = "EVENT_ID")
  private EventEntity           event;

  @Column(name = "IDENTITY_ID", nullable = false)
  private long                  identityId;

  @Column(name = "FROM_OCCURRENCE_ID")
  private Date                  fromOccurrenceId;

  @Column(name = "UNTIL_OCCURRENCE_ID")
  private Date                  untilOccurrenceId;

  @Column(name = "RESPONSE", nullable = false)
  private EventAttendeeResponse response;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public EventEntity getEvent() {
    return event;
  }

  public void setEvent(EventEntity event) {
    this.event = event;
  }

  public long getIdentityId() {
    return identityId;
  }

  public void setIdentityId(long identityId) {
    this.identityId = identityId;
  }

  public EventAttendeeResponse getResponse() {
    return response;
  }

  public void setResponse(EventAttendeeResponse response) {
    this.response = response;
  }

  public Date getFromOccurrenceId() {
    return fromOccurrenceId;
  }

  public Date getUntilOccurrenceId() {
    return untilOccurrenceId;
  }

  public void setFromOccurrenceId(Date fromOccurrenceId) {
    this.fromOccurrenceId = fromOccurrenceId;
  }

  public void setUntilOccurrenceId(Date untilOccurrenceId) {
    this.untilOccurrenceId = untilOccurrenceId;
  }
}
