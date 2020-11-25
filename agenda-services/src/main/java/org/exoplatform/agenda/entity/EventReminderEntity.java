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

import org.exoplatform.agenda.constant.ReminderPeriodType;
import org.exoplatform.commons.api.persistence.ExoEntity;

@Entity(name = "AgendaEventReminder")
@ExoEntity
@Table(name = "EXO_AGENDA_REMINDER")
@NamedQueries(
  {
      @NamedQuery(
          name = "AgendaEventReminder.deleteCalendarReminders",
          query = "DELETE FROM AgendaEventReminder a WHERE a.event.id IN (SELECT evt.id FROM AgendaEvent evt WHERE evt.calendar.id = :calendarId)"
      ),
      @NamedQuery(
          name = "AgendaEventReminder.deleteEventReminders",
          query = "DELETE FROM AgendaEventReminder a WHERE a.event.id = :eventId"
      ),
      @NamedQuery(
          name = "AgendaEventReminder.getEventRemindersByEventIdAndUserId",
          query = "SELECT a FROM AgendaEventReminder a WHERE a.event.id = :eventId AND a.receiverId = :userId"
      ),
      @NamedQuery(
          name = "AgendaEventReminder.getEventRemindersByEventId",
          query = "SELECT a FROM AgendaEventReminder a WHERE a.event.id = :eventId"
      ),
      @NamedQuery(
          name = "AgendaEventReminder.getEventRemindersByPeriod",
          query = "SELECT a FROM AgendaEventReminder a WHERE a.triggerDate >= :start AND a.triggerDate < :end"
      ),
  }
)
public class EventReminderEntity implements Serializable {

  private static final long  serialVersionUID = 6460217989840428489L;

  @Id
  @SequenceGenerator(name = "SEQ_AGENDA_EVENT_REMINDER_ID", sequenceName = "SEQ_AGENDA_EVENT_REMINDER_ID")
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_AGENDA_EVENT_REMINDER_ID")
  @Column(name = "EVENT_REMINDER_ID")
  private Long               id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "EVENT_ID", referencedColumnName = "EVENT_ID")
  private EventEntity        event;

  @Column(name = "RECEIVER_ID", nullable = false)
  private long               receiverId;

  @Column(name = "BEFORE_VALUE", nullable = false)
  private int                before;

  @Column(name = "BEFORE_PERIOD_TYPE", nullable = false)
  private ReminderPeriodType beforeType;

  @Column(name = "TRIGGER_DATE", nullable = false)
  private Date               triggerDate;

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

  public int getBefore() {
    return before;
  }

  public void setBefore(int before) {
    this.before = before;
  }

  public ReminderPeriodType getBeforeType() {
    return beforeType;
  }

  public void setBeforeType(ReminderPeriodType beforeType) {
    this.beforeType = beforeType;
  }

  public Date getTriggerDate() {
    return triggerDate;
  }

  public void setTriggerDate(Date triggerDate) {
    this.triggerDate = triggerDate;
  }

  public long getReceiverId() {
    return receiverId;
  }

  public void setReceiverId(long receiverId) {
    this.receiverId = receiverId;
  }
}
