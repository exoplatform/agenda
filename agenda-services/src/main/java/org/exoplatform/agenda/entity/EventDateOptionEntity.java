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
