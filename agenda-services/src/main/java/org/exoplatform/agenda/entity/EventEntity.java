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
import java.util.List;

import javax.persistence.*;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.commons.api.persistence.ExoEntity;

@Entity(name = "AgendaEvent")
@ExoEntity
@Table(name = "EXO_AGENDA_EVENT")
@NamedQueries(
  {
      @NamedQuery(
          name = "AgendaEvent.deleteCalendarEvents", query = "DELETE FROM AgendaEvent ev WHERE ev.calendar.id = :calendarId"
      ),
      @NamedQuery(
          name = "AgendaEvent.getEventIdsByPeriodAndOwnerIds", query = "SELECT DISTINCT(ev.id) FROM AgendaEvent ev"
              + " INNER JOIN ev.attendees att"
              + " INNER JOIN ev.calendar cal"
              + " WHERE (att.identityId IN (:ownerIds) OR cal.ownerId IN (:ownerIds))"
              + " AND ev.startDate < :end"
              + " AND (ev.endDate IS NULL OR ev.endDate >= :start)"
      ),
      @NamedQuery(
          name = "AgendaEvent.getEventIdsByPeriodAndAttendeeIds", query = "SELECT DISTINCT(ev.id) FROM AgendaEvent ev"
              + " INNER JOIN ev.attendees att"
              + " WHERE att.identityId IN (:attendeeIds)"
              + " AND ev.startDate < :end"
              + " AND (ev.endDate IS NULL OR ev.endDate >= :start)"
      ),
      @NamedQuery(
          name = "AgendaEvent.getEventIdsByPeriodAndAttendeeIdsAndOwnerIds", query = "SELECT DISTINCT(ev.id) FROM AgendaEvent ev"
              + " INNER JOIN ev.attendees att"
              + " INNER JOIN ev.calendar cal"
              + " WHERE att.identityId IN (:attendeeIds)"
              + " AND cal.ownerId IN (:ownerIds)"
              + " AND ev.startDate < :end"
              + " AND (ev.endDate IS NULL OR ev.endDate >= :start)"
      ),
      @NamedQuery(
          name = "AgendaEvent.getExceptionalOccurenceEventIds", query = "SELECT DISTINCT(ev.id) FROM AgendaEvent ev"
              + " WHERE ev.parent.id = :parentEventId"
              + " AND ev.occurrenceId <= :end"
              + " AND ev.occurrenceId >= :start"
      ),
  }
)
public class EventEntity implements Serializable {

  private static final long         serialVersionUID = -597472315530960636L;

  @Id
  @SequenceGenerator(name = "SEQ_AGENDA_EVENT_ID", sequenceName = "SEQ_AGENDA_EVENT_ID")
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_AGENDA_EVENT_ID")
  @Column(name = "EVENT_ID")
  private Long                      id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "PARENT_EVENT_ID", referencedColumnName = "EVENT_ID")
  private EventEntity               parent;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "CALENDAR_ID", referencedColumnName = "CALENDAR_ID")
  private CalendarEntity            calendar;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "REMOTE_PROVIDER_ID", referencedColumnName = "AGENDA_PROVIDER_ID")
  private RemoteProviderEntity      remoteProvider;

  @OneToMany(mappedBy = "event", fetch = FetchType.LAZY)
  private List<EventAttendeeEntity> attendees;

  @Column(name = "REMOTE_ID")
  private String                    remoteId;

  @OneToOne(mappedBy = "event", fetch = FetchType.EAGER)
  private EventRecurrenceEntity     recurrence;

  @Column(name = "OCCURRENCE_ID")
  private Date                      occurrenceId;

  @Column(name = "CREATOR_ID", nullable = false)
  private long                      creatorId;

  @Column(name = "CREATED_DATE", nullable = false)
  private Date                      createdDate;

  @Column(name = "UPDATED_DATE")
  private Date                      updatedDate;

  @Column(name = "ALL_DAY")
  private boolean                   allDay;

  @Column(name = "START_DATE")
  private Date                      startDate;

  @Column(name = "END_DATE")
  private Date                      endDate;

  @Column(name = "SUMMARY")
  private String                    summary;

  @Column(name = "DESCRIPTION")
  private String                    description;

  @Column(name = "LOCATION")
  private String                    location;

  @Column(name = "COLOR")
  private String                    color;

  @Column(name = "AVAILABILITY", nullable = false)
  private EventAvailability         availability;

  @Column(name = "STATUS", nullable = false)
  private EventStatus               status;

  @Column(name = "ALLOW_ATTENDEE_TO_UPDATE", nullable = false)
  private boolean                   allowAttendeeToUpdate;

  @Column(name = "ALLOW_ATTENDEE_TO_INVITE", nullable = false)
  private boolean                   allowAttendeeToInvite;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public EventEntity getParent() {
    return parent;
  }

  public void setParent(EventEntity parent) {
    this.parent = parent;
  }

  public CalendarEntity getCalendar() {
    return calendar;
  }

  public void setCalendar(CalendarEntity calendar) {
    this.calendar = calendar;
  }

  public RemoteProviderEntity getRemoteProvider() {
    return remoteProvider;
  }

  public void setRemoteProvider(RemoteProviderEntity remoteProvider) {
    this.remoteProvider = remoteProvider;
  }

  public String getRemoteId() {
    return remoteId;
  }

  public void setRemoteId(String remoteId) {
    this.remoteId = remoteId;
  }

  public EventRecurrenceEntity getRecurrence() {
    return recurrence;
  }

  public void setRecurrence(EventRecurrenceEntity recurrence) {
    this.recurrence = recurrence;
  }

  public Date getOccurrenceId() {
    return occurrenceId;
  }

  public void setOccurrenceId(Date occurrenceId) {
    this.occurrenceId = occurrenceId;
  }

  public long getCreatorId() {
    return creatorId;
  }

  public void setCreatorId(long creatorId) {
    this.creatorId = creatorId;
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

  public boolean isAllDay() {
    return allDay;
  }

  public void setAllDay(boolean allDay) {
    this.allDay = allDay;
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

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public EventAvailability getAvailability() {
    return availability;
  }

  public void setAvailability(EventAvailability availability) {
    this.availability = availability;
  }

  public EventStatus getStatus() {
    return status;
  }

  public void setStatus(EventStatus status) {
    this.status = status;
  }

  public boolean isAllowAttendeeToUpdate() {
    return allowAttendeeToUpdate;
  }

  public void setAllowAttendeeToUpdate(boolean allowAttendeeToUpdate) {
    this.allowAttendeeToUpdate = allowAttendeeToUpdate;
  }

  public boolean isAllowAttendeeToInvite() {
    return allowAttendeeToInvite;
  }

  public void setAllowAttendeeToInvite(boolean allowAttendeeToInvite) {
    this.allowAttendeeToInvite = allowAttendeeToInvite;
  }

}
