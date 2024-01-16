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
          name = "AgendaEvent.deleteCalendarEvents",
          query = "DELETE FROM AgendaEvent ev WHERE ev.calendar.id = :calendarId"
      ),
      @NamedQuery(
          name = "AgendaEvent.getCalendarEventIds",
          query = "SELECT ev.id FROM AgendaEvent ev WHERE ev.calendar.id = :calendarId"
      ),
      @NamedQuery(
          name = "AgendaEvent.getExceptionalOccurenceIdsByPeriod",
          query = "SELECT ev.id FROM AgendaEvent ev"
              + " WHERE ev.parent.id = :parentEventId"
              + " AND ev.occurrenceId <= :end"
              + " AND ev.occurrenceId >= :start"
      ),
      @NamedQuery(
          name = "AgendaEvent.getExceptionalOccurenceIdsByStart",
          query = "SELECT ev.id FROM AgendaEvent ev"
              + " WHERE ev.parent.id = :parentEventId"
              + " AND ev.occurrenceId >= :start"
      ),
      @NamedQuery(
          name = "AgendaEvent.getExceptionalOccurenceIds",
          query = "SELECT ev.id, ev.startDate FROM AgendaEvent ev"
              + " WHERE ev.parent.id = :parentEventId"
              + " AND ev.occurrenceId IS NOT NULL"
              + " ORDER BY ev.startDate ASC"
      ),
      @NamedQuery(
          name = "AgendaEvent.getExceptionalOccurences",
          query = "SELECT ev FROM AgendaEvent ev"
              + " WHERE ev.parent.id = :parentEventId"
              + " AND ev.occurrenceId IS NOT NULL"
      ),
      @NamedQuery(
          name = "AgendaEvent.getChildEvents",
          query = "SELECT ev.id FROM AgendaEvent ev"
              + " WHERE ev.parent.id = :parentEventId"
      ),
      @NamedQuery(
          name = "AgendaEvent.getParentRecurrentEventIds",
          query = "SELECT ev FROM AgendaEvent ev"
              + " WHERE ev.status = :status"
              + " AND ev.recurrence.id > 0"
              + " AND ev.occurrenceId IS NULL"
              + " AND ev.startDate < :end"
              + " AND (ev.endDate IS NULL OR ev.endDate >= :start)"
      ),
      @NamedQuery(
          name = "AgendaEvent.getPendingEventIds",
          query = "SELECT DISTINCT(ev.id), ev.updatedDate FROM AgendaEvent ev"
              + " INNER JOIN ev.attendees att"
              + " INNER JOIN ev.calendar cal"
              + " WHERE ev.status = :status"
              + " AND (ev.endDate IS NULL OR ev.endDate > :date)"
              + " AND ("
              + "   ev.occurrencePeriodChanged = TRUE"
              + "   OR ev.parent IS NULL"
              + " )"
              + " AND att.identityId IN (:attendeeIds)"
              + " AND att.response = :response"
              + " AND cal.id IN (:calenderIds)"
              + " AND NOT EXISTS("
              + "   SELECT att2.id FROM AgendaEventAttendee att2"
              + "   WHERE att2.event.id = ev.id"
              + "         AND att2.identityId = :userIdentityId"
              + "         AND att2.response != :response"
              + " )"
              + " ORDER BY ev.updatedDate DESC"
      ),
      @NamedQuery(
          name = "AgendaEvent.countPendingEvents",
          query = "SELECT count(DISTINCT ev.id) FROM AgendaEvent ev"
              + " INNER JOIN ev.attendees att"
              + " INNER JOIN ev.calendar cal"
              + " WHERE ev.status = :status"
              + " AND (ev.endDate IS NULL OR ev.endDate > :date)"
              + " AND ("
              + "   ev.occurrencePeriodChanged = TRUE"
              + "   OR ev.parent IS NULL"
              + " )"
              + " AND att.identityId IN (:attendeeIds)"
              + " AND att.response = :response"
              + " AND cal.id IN (:calenderIds)"
              + " AND NOT EXISTS("
              + "   SELECT att2.id FROM AgendaEventAttendee att2"
              + "   WHERE att2.event.id = ev.id"
              + "         AND att2.identityId = :userIdentityId"
              + "         AND att2.response != :response"
              + " )"
      ),
      @NamedQuery(
          name = "AgendaEvent.countPendingEventsByOwnerIds",
          query = "SELECT count(DISTINCT ev.id) FROM AgendaEvent ev"
              + " INNER JOIN ev.attendees att"
              + " INNER JOIN ev.calendar cal"
              + " WHERE ev.status = :status"
              + " AND (ev.endDate IS NULL OR ev.endDate > :date)"
              + " AND ("
              + "   ev.occurrencePeriodChanged = TRUE"
              + "   OR ev.parent IS NULL"
              + " )"
              + " AND cal.ownerId IN (:ownerIds)"
              + " AND att.identityId IN (:attendeeIds)"
              + " AND att.response = :response"
              + " AND NOT EXISTS("
              + "   SELECT att2.id FROM AgendaEventAttendee att2"
              + "   WHERE att2.event.id = ev.id"
              + "         AND att2.identityId = :userIdentityId"
              + "         AND att2.response != :response"
              + " )"
      ),
      @NamedQuery(
          name = "AgendaEvent.getPendingEventIdsByOwnerIds",
          query = "SELECT DISTINCT(ev.id), ev.updatedDate FROM AgendaEvent ev"
              + " INNER JOIN ev.attendees att"
              + " INNER JOIN ev.calendar cal"
              + " WHERE ev.status = :status"
              + " AND (ev.endDate IS NULL OR ev.endDate > :date)"
              + " AND ("
              + "   ev.occurrencePeriodChanged = TRUE"
              + "   OR ev.parent IS NULL"
              + " )"
              + " AND cal.ownerId IN (:ownerIds)"
              + " AND att.identityId IN (:attendeeIds)"
              + " AND att.response = :response"
              + " AND NOT EXISTS("
              + "   SELECT att2.id FROM AgendaEventAttendee att2"
              + "   WHERE att2.event.id = ev.id"
              + "         AND att2.identityId = :userIdentityId"
              + "         AND att2.response != :response"
              + " )"
              + " ORDER BY ev.updatedDate DESC"
      ),
      @NamedQuery(
          name = "AgendaEvent.getPendingDatePollIds",
          query = "SELECT DISTINCT(ev.id), ev.updatedDate FROM AgendaEvent ev"
              + " INNER JOIN ev.attendees att"
              + " WHERE ev.status = :status"
              + " AND (ev.endDate IS NULL OR ev.endDate > :date OR ev.creatorId = :userIdentityId)"
              + " AND att.identityId IN (:attendeeIds)"
              + " ORDER BY ev.updatedDate DESC"
      ),
      @NamedQuery(
          name = "AgendaEvent.getDatePollIdsByDates",
          query = "SELECT DISTINCT(ev.id), ev.updatedDate FROM AgendaEvent ev"
              + " INNER JOIN ev.attendees att"
              + " WHERE ev.status = :status"
              + " AND (ev.endDate IS NULL OR ev.endDate > :start)"
              + " AND (ev.startDate < :end)"
              + " AND att.identityId IN (:attendeeIds)"
              + " ORDER BY ev.updatedDate DESC"
      ),
      @NamedQuery(
          name = "AgendaEvent.countPendingDatePoll",
          query = "SELECT count(DISTINCT ev.id) FROM AgendaEvent ev"
              + " INNER JOIN ev.attendees att"
              + " WHERE ev.status = :status"
              + " AND (ev.endDate IS NULL OR ev.endDate > :date)"
              + " AND att.identityId IN (:attendeeIds)"
      ),
      @NamedQuery(
          name = "AgendaEvent.getDatePollIdsByOwnerIdsAndDates",
          query = "SELECT DISTINCT(ev.id), ev.updatedDate FROM AgendaEvent ev"
              + " INNER JOIN ev.attendees att"
              + " INNER JOIN ev.calendar cal"
              + " WHERE ev.status = :status"
              + " AND (ev.endDate IS NULL OR ev.endDate > :start)"
              + " AND (ev.startDate < :end)"
              + " AND att.identityId IN (:attendeeIds)"
              + " AND cal.ownerId IN (:ownerIds)"
              + " ORDER BY ev.updatedDate DESC"
      ),
      @NamedQuery(
          name = "AgendaEvent.getPendingDatePollIdsByOwnerIds",
          query = "SELECT DISTINCT(ev.id), ev.updatedDate FROM AgendaEvent ev"
              + " INNER JOIN ev.attendees att"
              + " INNER JOIN ev.calendar cal"
              + " WHERE ev.status = :status"
              + " AND (ev.endDate IS NULL OR ev.endDate > :date OR ev.creatorId = :userIdentityId)"
              + " AND att.identityId IN (:attendeeIds)"
              + " AND cal.ownerId IN (:ownerIds)"
              + " ORDER BY ev.updatedDate DESC"
      ),
      @NamedQuery(
          name = "AgendaEvent.countPendingDatePollByOwnerIds",
          query = "SELECT count(DISTINCT ev.id) FROM AgendaEvent ev"
              + " INNER JOIN ev.attendees att"
              + " INNER JOIN ev.calendar cal"
              + " WHERE ev.status = :status"
              + " AND (ev.endDate IS NULL OR ev.endDate > :date)"
              + " AND att.identityId IN (:attendeeIds)"
              + " AND cal.ownerId IN (:ownerIds)"
      ),
      @NamedQuery(
          name = "AgendaEvent.getUserEventCalenderIds",
          query = "SELECT DISTINCT cal.id FROM AgendaEvent ev"
              + " INNER JOIN ev.attendees att"
              + " INNER JOIN ev.calendar cal"
              + " WHERE att.identityId = :userIdentityId "
      ),
  }
)
public class EventEntity implements Serializable {

  private static final long         serialVersionUID = -597472315530960636L;

  @Id
  @SequenceGenerator(name = "SEQ_AGENDA_EVENT_ID", sequenceName = "SEQ_AGENDA_EVENT_ID", allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_AGENDA_EVENT_ID")
  @Column(name = "EVENT_ID")
  private Long                      id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "PARENT_EVENT_ID", referencedColumnName = "EVENT_ID")
  private EventEntity               parent;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "CALENDAR_ID", referencedColumnName = "CALENDAR_ID")
  private CalendarEntity            calendar;

  @OneToMany(mappedBy = "event", fetch = FetchType.LAZY)
  private List<EventAttendeeEntity> attendees;

  @OneToOne(mappedBy = "event", fetch = FetchType.EAGER)
  private EventRecurrenceEntity     recurrence;

  @Column(name = "OCCURRENCE_ID")
  private Date                      occurrenceId;

  @Column(name = "CREATOR_ID", nullable = false)
  private long                      creatorId;

  @Column(name = "MODIFIER_ID", nullable = false)
  private long                      modifierId;

  @Column(name = "TIMEZONE_ID")
  private String                    timeZoneId;

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

  @Column(name = "OCCURRENCE_PERIOD_CHANGED")
  private boolean                   occurrencePeriodChanged;

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

  public long getModifierId() {
    return modifierId;
  }

  public void setModifierId(long modifierId) {
    this.modifierId = modifierId;
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

  public String getTimeZoneId() {
    return timeZoneId;
  }

  public void setTimeZoneId(String timeZoneId) {
    this.timeZoneId = timeZoneId;
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

  public boolean isOccurrencePeriodChanged() {
    return occurrencePeriodChanged;
  }

  public void setOccurrencePeriodChanged(boolean occurrencePeriodChanged) {
    this.occurrencePeriodChanged = occurrencePeriodChanged;
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
