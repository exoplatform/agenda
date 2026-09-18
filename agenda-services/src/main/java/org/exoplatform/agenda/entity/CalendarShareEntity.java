/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.entity;

import java.io.Serializable;
import java.util.Date;

import org.hibernate.annotations.DynamicUpdate;

import org.exoplatform.agenda.constant.CalendarShareLevel;
import org.exoplatform.agenda.constant.CalendarShareSource;

import io.meeds.common.persistence.PortableSequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The stored side of a calendar share (EXO-90357): which calendar, which
 * colleague, who granted it, when, where it came from, which channel also
 * carries it, and whether the colleague hid it.
 * <p>
 * {@code (CALENDAR_ID, SHAREE_IDENTITY_ID)} is unique in the table: a calendar
 * is shared once with a colleague, and sharing again is a retry of the
 * delivery, never a second row. {@link DynamicUpdate} because the row has two
 * writers — the owner's delivery updates {@code DELIVERED_TO} while the sharee
 * may be flipping {@code HIDDEN}, and the owner's level change writes
 * {@code ACCESS_LEVEL} (EXO-90378) — and each must write only its own column.
 */
@Entity(name = "AgendaCalendarShare")
@Table(name = "EXO_AGENDA_CALENDAR_SHARE")
@DynamicUpdate
@Getter
@Setter
public class CalendarShareEntity implements Serializable {

  private static final long   serialVersionUID = -2337604176145118225L;

  /** Technical identifier of the row. */
  @Id
  @PortableSequence(name = "SEQ_AGENDA_CALENDAR_SHARE_ID")
  @Column(name = "SHARE_ID")
  private Long                id;

  /** The shared calendar. */
  @Column(name = "CALENDAR_ID", nullable = false)
  private long                calendarId;

  /** Identity identifier of the colleague the calendar is shared with. */
  @Column(name = "SHAREE_IDENTITY_ID", nullable = false)
  private long                shareeIdentityId;

  /**
   * What the colleague may do with the calendar (EXO-90378), stored by name
   * beside {@code SOURCE}: {@code VIEW} or {@code EDIT}. Not null, and
   * {@code VIEW} by the column's default, so every row written before the
   * column existed reads as the level it was granted at.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "ACCESS_LEVEL", nullable = false)
  private CalendarShareLevel  level;

  /** Identity identifier of the user who shared or recorded the calendar. */
  @Column(name = "GRANTED_BY_IDENTITY_ID", nullable = false)
  private long                grantedById;

  /** When the row was created. */
  @Column(name = "CREATED_DATE", nullable = false)
  private Date                createdDate;

  /** Where the record came from, stored by name. */
  @Enumerated(EnumType.STRING)
  @Column(name = "SOURCE", nullable = false)
  private CalendarShareSource source;

  /** The channel that also carries the share, null when eXo only. */
  @Column(name = "DELIVERED_TO")
  private String              deliveredTo;

  /** What the channel handed back at delivery, null when nothing was delivered. */
  @Column(name = "DELIVERY_REF")
  private String              deliveryRef;

  /** Whether the sharee hid the calendar from their agenda. */
  @Column(name = "HIDDEN", nullable = false)
  private boolean             hidden;

}
