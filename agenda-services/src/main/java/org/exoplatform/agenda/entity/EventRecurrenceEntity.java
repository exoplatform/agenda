// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.*;

import org.exoplatform.agenda.constant.EventRecurrenceType;
import org.exoplatform.commons.api.persistence.ExoEntity;

@Entity(name = "AgendaEventRecurrence")
@ExoEntity
@Table(name = "EXO_AGENDA_RECURRENCE")
@NamedQueries(
  {
      @NamedQuery(
          name = "AgendaEventRecurrence.deleteCalendarRecurrences",
          query = "DELETE FROM AgendaEventRecurrence a WHERE a.event.id IN (SELECT evt.id FROM AgendaEvent evt WHERE evt.calendar.id = :calendarId)"
      ),
      @NamedQuery(
          name = "AgendaEventRecurrence.deleteEventRecurrences",
          query = "DELETE FROM AgendaEventRecurrence a WHERE a.event.id = :eventId"
      ), }
)
public class EventRecurrenceEntity implements Serializable {

  private static final long   serialVersionUID = -4214007539857435152L;

  @Id
  @SequenceGenerator(name = "SEQ_AGENDA_EVENT_RECURRENCE_ID", sequenceName = "SEQ_AGENDA_EVENT_RECURRENCE_ID")
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_AGENDA_EVENT_RECURRENCE_ID")
  @Column(name = "EVENT_RECURRENCE_ID")
  private Long                id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "EVENT_ID", referencedColumnName = "EVENT_ID")
  private EventEntity         event;

  @Column(name = "RECURRENCE_START_DATE", nullable = false)
  private Date                startDate;

  @Column(name = "RECURRENCE_END_DATE", nullable = false)
  private Date                endDate;

  @Column(name = "RECURRENCE_TYPE")
  private EventRecurrenceType type;

  @Column(name = "RECURRENCE_RRULE")
  private String              rrule;

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

  public Date getStartDate() {
    return startDate;
  }

  public void setStartDate(Date startDate) {
    this.startDate = startDate;
  }

  public Date getEndDate() {
    return endDate;
  }

  public void setEndDate(Date endDate) {
    this.endDate = endDate;
  }

  public String getRrule() {
    return rrule;
  }

  public void setRrule(String rrule) {
    this.rrule = rrule;
  }

  public EventRecurrenceType getType() {
    return type;
  }

  public void setType(EventRecurrenceType type) {
    this.type = type;
  }
}
