// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.*;

import org.exoplatform.commons.api.persistence.ExoEntity;

@Entity(name = "AgendaCalendar")
@ExoEntity
@Table(name = "EXO_AGENDA_CALENDAR")
@NamedQueries(
  {
      @NamedQuery(
          name = "AgendaCalendar.getCalendarIdsByOwnerIds",
          query = "SELECT cal.id FROM AgendaCalendar cal WHERE cal.ownerId IN (:ownerIds) ORDER BY cal.id ASC"
      ),
      @NamedQuery(
          name = "AgendaCalendar.countCalendarsByOwnerIds",
          query = "SELECT count(cal.id) FROM AgendaCalendar cal WHERE cal.ownerId IN (:ownerIds)"
      ),
  }
)
public class CalendarEntity implements Serializable {

  private static final long serialVersionUID = -5042089789130151840L;

  @Id
  @SequenceGenerator(name = "SEQ_AGENDA_CALENDAR_ID", sequenceName = "SEQ_AGENDA_CALENDAR_ID")
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_AGENDA_CALENDAR_ID")
  @Column(name = "CALENDAR_ID")
  private Long              id;

  @Column(name = "OWNER_ID", nullable = false)
  private Long              ownerId;

  @Column(name = "IS_SYSTEM")
  private boolean           isSystem;

  @Column(name = "DESCRIPTION")
  private String            description;

  @Column(name = "COLOR", nullable = false)
  private String            color;

  @Column(name = "CREATED_DATE", nullable = false)
  private Date              createdDate;

  @Column(name = "UPDATED_DATE")
  private Date              updatedDate;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(Long ownerId) {
    this.ownerId = ownerId;
  }

  public boolean isSystem() {
    return isSystem;
  }

  public void setSystem(boolean isSystem) {
    this.isSystem = isSystem;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public Date getCreatedDate() {
    return createdDate;
  }

  public void setCreatedDate(Date createdDate) {
    this.createdDate = createdDate;
  }

  public Date getUpdatedDate() {
    return updatedDate;
  }

  public void setUpdatedDate(Date updatedDate) {
    this.updatedDate = updatedDate;
  }

}
