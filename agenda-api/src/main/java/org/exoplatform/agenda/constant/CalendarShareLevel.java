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
package org.exoplatform.agenda.constant;

/**
 * What a colleague may do with a calendar its owner shared with them
 * (EXO-90378).
 * <p>
 * Two levels, and only two: the owner picks one per colleague, and every write
 * path asks this one value. {@link #VIEW} is the level of EXO-90357 and the
 * default of every share, existing rows included — the column was added with
 * it as its default, so nothing an owner shared before this feature changes
 * hands.
 * <p>
 * The level says nothing about the calendar itself: an {@link #EDIT} colleague
 * writes <b>events</b> in the calendar and nothing more. Renaming, recolouring,
 * deleting, publishing, sharing on and levelling anyone stay the owner's, at
 * every level — see {@code Utils.checkAclByCalendarOwner},
 * {@code Utils.canPublishCalendar} and
 * {@code AgendaCalendarShareServiceImpl.getOwnedCalendar}, none of which this
 * enum reaches.
 */
public enum CalendarShareLevel {

  /**
   * The colleague reads the calendar and its events, and nothing else: no
   * creation, no change, no deletion, no reminder of their own. A private event
   * is busy time for them ({@code EventAccess.SHARED}).
   */
  VIEW,

  /**
   * The colleague creates, changes and deletes events in the owner's calendar
   * as the owner would, sees private events in full and may set their own
   * reminders on them ({@code EventAccess.SHARED_EDIT}). They may not move an
   * event out of the calendar — the event is the owner's — nor touch the
   * calendar itself.
   */
  EDIT;

  /**
   * The level a stored or transmitted name stands for, {@link #VIEW} for
   * anything that is not a level this version knows. Reading an unknown name as
   * the lower level is the same rule the whole share code follows: what cannot
   * be established grants nothing.
   *
   * @param name the name of a level, may be null or blank
   * @return the level, never null
   */
  public static CalendarShareLevel of(String name) {
    if (name == null) {
      return VIEW;
    }
    for (CalendarShareLevel level : values()) {
      if (level.name().equalsIgnoreCase(name.trim())) {
        return level;
      }
    }
    return VIEW;
  }

}
