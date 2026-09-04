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

import io.meeds.common.persistence.PortableSequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

@Entity(name = "AgendaEventDatePoll")
@Table(name = "EXO_AGENDA_EVENT_DATE_POLL")
@NamedQuery(
  name = "AgendaEventDatePoll.findDatePollByEventId",
  query = "SELECT datePoll FROM AgendaEventDatePoll datePoll WHERE datePoll.eventId = :eventId"
)
public class EventDatePollEntity implements Serializable {

  private static final long serialVersionUID = -6015331866476045556L;

  @Id
  @PortableSequence(name = "SEQ_AGENDA_EVENT_DATE_POLL_ID")
  @Column(name = "EVENT_DATE_POLL_ID")
  private Long              id;

  @Column(name = "EVENT_ID", nullable = false)
  private Long              eventId;

  @Column(name = "SELECTED_DATE_OPTION_ID")
  private Long              selectedDateOptionId;

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

  public Long getSelectedDateOptionId() {
    return selectedDateOptionId;
  }

  public void setSelectedDateOptionId(Long selectedDateOptionId) {
    this.selectedDateOptionId = selectedDateOptionId;
  }
}
