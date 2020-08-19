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

import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.commons.api.persistence.ExoEntity;

@Entity(name = "AgendaEventRecurrence")
@ExoEntity
@Table(name = "EXO_AGENDA_RECURRENCE")
@NamedQueries({
  @NamedQuery(name = "AgendaEventRecurrence.deleteCalendarRecurrences", query = "DELETE FROM AgendaEventRecurrence a WHERE a.event.id IN (SELECT evt.id FROM AgendaEvent evt WHERE evt.calendar.id = :calendarId)"),
  @NamedQuery(name = "AgendaEventRecurrence.deleteEventRecurrences", query = "DELETE FROM AgendaEventRecurrence a WHERE a.event.id = :eventId"),
})
public class EventRecurrenceEntity implements Serializable {

  private static final long        serialVersionUID = -4214007539857435152L;

  @Id
  @SequenceGenerator(name = "SEQ_AGENDA_EVENT_RECURRENCE_ID", sequenceName = "SEQ_AGENDA_EVENT_RECURRENCE_ID")
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_AGENDA_EVENT_RECURRENCE_ID")
  @Column(name = "EVENT_RECURRENCE_ID")
  private Long                     id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "EVENT_ID", referencedColumnName = "EVENT_ID")
  private EventEntity              event;

  @Column(name = "RECURRENCE_START_DATE", nullable = false)
  private Date                     startDate;

  @Column(name = "RECURRENCE_END_DATE", nullable = false)
  private Date                     endDate;

  @Column(name = "RECURRENCE_FREQUENCY", nullable = false)
  private EventRecurrenceFrequency frquency;

  @Column(name = "RECURRENCE_INTERVAL", nullable = false)
  private int                      interval;

  @Column(name = "RECURRENCE_UNTIL_DATE")
  private Date                     untilDate;

  @Column(name = "RECURRENCE_COUNT")
  private int                      count;

  @Column(name = "RECURRENCE_BY")
  private String                   by;

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

  public EventRecurrenceFrequency getFrquency() {
    return frquency;
  }

  public void setFrquency(EventRecurrenceFrequency frquency) {
    this.frquency = frquency;
  }

  public int getInterval() {
    return interval;
  }

  public void setInterval(int interval) {
    this.interval = interval;
  }

  public Date getUntilDate() {
    return untilDate;
  }

  public void setUntilDate(Date untilDate) {
    this.untilDate = untilDate;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }

  public String getBy() {
    return by;
  }

  public void setBy(String by) {
    this.by = by;
  }

}
