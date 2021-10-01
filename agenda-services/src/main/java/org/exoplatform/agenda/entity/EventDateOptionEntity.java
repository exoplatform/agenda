// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.*;

import org.exoplatform.commons.api.persistence.ExoEntity;

@Entity(name = "AgendaEventDateOption")
@ExoEntity
@Table(name = "EXO_AGENDA_EVENT_DATE_OPTION")
@NamedQueries(
  {
      @NamedQuery(
          name = "AgendaEventDateOption.findDateOptionsByEventId",
          query = "SELECT dateOption from AgendaEventDateOption dateOption WHERE dateOption.eventId = :eventId"
      ),
  }
)
public class EventDateOptionEntity implements Serializable {

  private static final long serialVersionUID = -508194709102103065L;

  @Id
  @SequenceGenerator(name = "SEQ_AGENDA_EVENT_DATE_OPTION_ID", sequenceName = "SEQ_AGENDA_EVENT_DATE_OPTION_ID")
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_AGENDA_EVENT_DATE_OPTION_ID")
  @Column(name = "EVENT_DATE_OPTION_ID")
  private Long              id;

  @Column(name = "EVENT_ID", nullable = false)
  private Long              eventId;

  @Column(name = "START_DATE")
  private Date              startDate;

  @Column(name = "END_DATE")
  private Date              endDate;

  @Column(name = "ALL_DAY")
  private boolean           allDay;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getEventId() {
    return eventId;
  }

  public void setEventId(Long eventId) {
    this.eventId = eventId;
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

  public boolean isAllDay() {
    return allDay;
  }

  public void setAllDay(boolean allDay) {
    this.allDay = allDay;
  }
}
