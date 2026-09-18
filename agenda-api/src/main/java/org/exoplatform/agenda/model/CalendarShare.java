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
package org.exoplatform.agenda.model;

import org.exoplatform.agenda.constant.CalendarShareLevel;
import org.exoplatform.agenda.constant.CalendarShareSource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One calendar shared, read-only, with one colleague (EXO-90357).
 * <p>
 * The record is eXo's own truth: a sharee reads the calendar and its events
 * because this row exists, whether or not a remote server also carries the
 * share. When a delivery channel (the CalDAV add-on) also granted the share on
 * its server, {@link #deliveredTo} names that channel and
 * {@link #deliveryRef} what the channel handed back to recognise its own
 * delivery again — the collection the colleague now sees, typically.
 * <p>
 * {@link #level} is what the colleague may do with it (EXO-90378): read it, or
 * also write events in it. Absent from a record read before that column
 * existed, and from one a caller builds without it — {@code null} is read as
 * {@link CalendarShareLevel#VIEW} by {@link #getLevel()}, never as the wider
 * level.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarShare implements Cloneable {

  /** Technical identifier of the record. */
  private long                id;

  /** Technical identifier of the shared calendar. */
  private long                calendarId;

  /** Identity identifier of the colleague the calendar is shared with. */
  private long                shareeIdentityId;

  /**
   * What the colleague may do with the calendar (EXO-90378). Never null on a
   * stored record; {@link #getLevel()} answers {@link CalendarShareLevel#VIEW}
   * for a record built without one.
   */
  private CalendarShareLevel  level;

  /** Identity identifier of the user who shared, or recorded, the calendar. */
  private long                grantedById;

  /** When the record was created, in milliseconds since the epoch. */
  private long                createdDate;

  /** Where the record came from. */
  private CalendarShareSource source;

  /**
   * The delivery channel that also carries the share — the plugin's
   * {@code CalendarShareChannelPlugin.id()}, or the qualified identifier it
   * stamped the delivery with, {@code caldav:<serverId>}, which agenda resolves
   * back to the plugin by its {@code id()} prefix — or null when the share
   * exists in eXo only.
   */
  private String              deliveredTo;

  /**
   * What the channel handed back at delivery to recognise it again: for CalDAV
   * the href of the collection the colleague now sees. Null when nothing was
   * delivered, or when the channel gave nothing back.
   */
  private String              deliveryRef;

  /**
   * Whether the sharee hid the calendar from their agenda. Hiding is the
   * sharee's choice and never deletes the share: the owner still lists the
   * colleague, and the colleague shows the calendar again from the Hidden
   * calendars of their settings.
   */
  private boolean             hidden;

  /**
   * What the colleague may do with the calendar, the narrower level for a
   * record that names none: a share whose level cannot be established grants
   * reading, never writing.
   *
   * @return the level, never null
   */
  public CalendarShareLevel getLevel() {
    return level == null ? CalendarShareLevel.VIEW : level;
  }

  /**
   * Whether the colleague may write events in the calendar.
   *
   * @return true when the level is {@link CalendarShareLevel#EDIT}
   */
  public boolean isEditor() {
    return getLevel() == CalendarShareLevel.EDIT;
  }

  /**
   * Copies the record.
   *
   * @return a copy
   */
  @Override
  public CalendarShare clone() { // NOSONAR
    return new CalendarShare(id,
                             calendarId,
                             shareeIdentityId,
                             level,
                             grantedById,
                             createdDate,
                             source,
                             deliveredTo,
                             deliveryRef,
                             hidden);
  }

}
