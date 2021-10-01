// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.entity;

import java.io.Serializable;

import javax.persistence.*;

import org.exoplatform.commons.api.persistence.ExoEntity;

@Entity(name = "AgendaRemoteEvent")
@ExoEntity
@Table(name = "EXO_AGENDA_REMOTE_EVENT")
@NamedQueries(
  {
      @NamedQuery(
          name = "AgendaRemoteEvent.deleteCalendarRemoteEvents",
          query = "DELETE FROM AgendaRemoteEvent r WHERE r.eventId IN (SELECT evt.id FROM AgendaEvent evt WHERE evt.calendar.id = :calendarId)"
      ),
      @NamedQuery(
          name = "AgendaRemoteEvent.deleteRemoteEvents",
          query = "DELETE FROM AgendaRemoteEvent r WHERE r.eventId = :eventId"
      ),
      @NamedQuery(
          name = "AgendaRemoteEvent.findRemoteEventByEventIdAndIdentityId",
          query = "SELECT r FROM AgendaRemoteEvent r WHERE r.eventId = :eventId AND r.identityId = :identityId"
      ),
  }
)
public class RemoteEventEntity implements Serializable {

  private static final long serialVersionUID = 6095103528261973968L;

  @Id
  @SequenceGenerator(name = "SEQ_AGENDA_REMOTE_EVENT_ID", sequenceName = "SEQ_AGENDA_REMOTE_EVENT_ID")
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_AGENDA_REMOTE_EVENT_ID")
  @Column(name = "REMOTE_EVENT_ID")
  private Long              id;

  /*
   * This column is joined in DB but no need to add a join annotaion here to
   * avoid extra DB queries when updating or creating entities
   */
  @Column(name = "REMOTE_PROVIDER_ID", nullable = false)
  private Long              remoteProviderId;

  /*
   * This column is joined in DB but no need to add a join annotaion here to
   * avoid extra DB queries when updating or creating entities
   */
  @Column(name = "EVENT_ID", nullable = false)
  private Long              eventId;

  @Column(name = "IDENTITY_ID", nullable = false)
  private long              identityId;

  @Column(name = "REMOTE_ID", nullable = false)
  private String            remoteId;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getRemoteProviderId() {
    return remoteProviderId;
  }

  public void setRemoteProviderId(Long remoteProviderId) {
    this.remoteProviderId = remoteProviderId;
  }

  public Long getEventId() {
    return eventId;
  }

  public void setEventId(Long eventId) {
    this.eventId = eventId;
  }

  public long getIdentityId() {
    return identityId;
  }

  public void setIdentityId(long identityId) {
    this.identityId = identityId;
  }

  public String getRemoteId() {
    return remoteId;
  }

  public void setRemoteId(String remoteId) {
    this.remoteId = remoteId;
  }

}
