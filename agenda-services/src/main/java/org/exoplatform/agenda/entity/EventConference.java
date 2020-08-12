/*
 * Copyright (C) 2020 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.agenda.entity;

import java.io.Serializable;

import javax.persistence.*;

import org.exoplatform.commons.api.persistence.ExoEntity;

@Entity(name = "EventConference")
@ExoEntity
@Table(name = "EXO_AGENDA_CONFERENCE")
public class EventConference implements Serializable {

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

  @Column(name = "URI")
  private String            uri;

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

  public String getUri() {
    return uri;
  }

  public void setUri(String uri) {
    this.uri = uri;
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
