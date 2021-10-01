// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.entity;

import java.io.Serializable;

import javax.persistence.*;

import org.exoplatform.commons.api.persistence.ExoEntity;

@Entity(name = "AgendaEventConference")
@ExoEntity
@Table(name = "EXO_AGENDA_CONFERENCE")
@NamedQueries(
  {
      @NamedQuery(
          name = "AgendaEventConference.deleteCalendarConferences",
          query = "DELETE FROM AgendaEventConference a WHERE a.event.id IN (SELECT evt.id FROM AgendaEvent evt WHERE evt.calendar.id = :calendarId)"
      ),
      @NamedQuery(
          name = "AgendaEventConference.deleteEventConferences",
          query = "DELETE FROM AgendaEventConference a WHERE a.event.id = :eventId"
      ),
      @NamedQuery(
          name = "AgendaEventConference.getEventConferencesByEventId",
          query = "SELECT a FROM AgendaEventConference a WHERE a.event.id = :eventId"
      ),
  }
)
public class EventConferenceEntity implements Serializable {

  private static final long serialVersionUID = -2825448569914546772L;

  @Id
  @SequenceGenerator(name = "SEQ_AGENDA_EVENT_CONFERENCE_ID", sequenceName = "SEQ_AGENDA_EVENT_CONFERENCE_ID")
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_AGENDA_EVENT_CONFERENCE_ID")
  @Column(name = "EVENT_CONFERENCE_ID")
  private Long              id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "EVENT_ID", referencedColumnName = "EVENT_ID")
  private EventEntity       event;

  @Column(name = "TYPE")
  private String            type;

  @Column(name = "URL")
  private String            url;

  @Column(name = "PHONE")
  private String            phone;

  @Column(name = "ACCESS_CODE")
  private String            accessCode;

  @Column(name = "DESCRIPTION")
  private String            description;

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

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getAccessCode() {
    return accessCode;
  }

  public void setAccessCode(String accessCode) {
    this.accessCode = accessCode;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

}
