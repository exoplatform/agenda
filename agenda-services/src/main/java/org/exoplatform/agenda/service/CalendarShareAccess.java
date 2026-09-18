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
package org.exoplatform.agenda.service;

import java.util.List;
import java.util.Map;

import org.exoplatform.agenda.constant.CalendarShareLevel;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * How agenda's Kernel-wired services reach the Spring-wired
 * {@link AgendaCalendarShareService} (EXO-90357).
 * <p>
 * {@code AgendaCalendarServiceImpl} and {@code AgendaEventServiceImpl} are
 * Kernel components built from {@code configuration.xml}; the share service is
 * a Spring bean, exported to the Kernel by the bridge once its context is up.
 * A constructor dependency is impossible in that direction, so the bean is
 * looked up the first time it is needed, as {@code AgendaEventReminderServiceImpl}
 * looks up the calendar service.
 * <p>
 * <b>Absent means not shared, never allowed.</b> Every answer here widens an
 * ACL: a lookup that fails, a bean that is not there yet, an exception inside
 * it, all read as "no share", so that nothing this class cannot establish ever
 * lets a reader through. Since EXO-90378 the same rule carries the level: a
 * level that cannot be established is <b>no level at all</b> — null, not
 * {@link CalendarShareLevel#VIEW} — so an unreachable share service admits no
 * reader and, a fortiori, no writer.
 */
public class CalendarShareAccess {

  private static final Log           LOG = ExoLogger.getLogger(CalendarShareAccess.class);

  private AgendaCalendarShareService calendarShareService;

  /**
   * Builds a lookup that resolves the bean on first use.
   */
  public CalendarShareAccess() {
    // The bean is resolved lazily
  }

  /**
   * Builds a lookup over a known service, for tests and for the Spring side.
   *
   * @param calendarShareService the share service, may be null
   */
  public CalendarShareAccess(AgendaCalendarShareService calendarShareService) {
    this.calendarShareService = calendarShareService;
  }

  /**
   * Whether a calendar is shared with an identity.
   *
   * @param calendarId technical identifier of the calendar
   * @param identityId identity identifier of the reader
   * @return true only when the share service says so
   */
  public boolean isSharedWith(long calendarId, long identityId) {
    AgendaCalendarShareService service = service();
    if (service == null || calendarId <= 0 || identityId <= 0) {
      return false;
    }
    try {
      return service.isSharedWith(calendarId, identityId);
    } catch (RuntimeException | LinkageError e) {
      LOG.warn("The shares of calendar {} could not be read for identity {}; it is read as not shared", calendarId, identityId, e);
      return false;
    }
  }

  /**
   * The level a calendar is shared with an identity at (EXO-90378).
   *
   * @param calendarId technical identifier of the calendar
   * @param identityId identity identifier of the reader
   * @return the level, or null when the calendar is not shared with them or
   *         the share service cannot say
   */
  public CalendarShareLevel levelOf(long calendarId, long identityId) {
    AgendaCalendarShareService service = service();
    if (service == null || calendarId <= 0 || identityId <= 0) {
      return null;
    }
    try {
      return service.getShareLevel(calendarId, identityId);
    } catch (RuntimeException | LinkageError e) {
      LOG.warn("The share level of calendar {} could not be read for identity {}; it is read as not shared",
               calendarId,
               identityId,
               e);
      return null;
    }
  }

  /**
   * Whether a calendar is shared with an identity at the level that lets them
   * write events in it. The one question every write path of
   * {@code AgendaEventServiceImpl} asks of a share.
   *
   * @param calendarId technical identifier of the calendar
   * @param identityId identity identifier of the user
   * @return true only when the share service says the level is
   *         {@link CalendarShareLevel#EDIT}
   */
  public boolean isSharedForEditWith(long calendarId, long identityId) {
    return levelOf(calendarId, identityId) == CalendarShareLevel.EDIT;
  }

  /**
   * Every calendar shared with an identity and the level of each (EXO-90378):
   * what a listing asks once instead of asking per event.
   *
   * @param identityId identity identifier of the reader
   * @return the levels by calendar identifier, empty when none or when they
   *         cannot be read
   */
  public Map<Long, CalendarShareLevel> levelsOf(long identityId) {
    AgendaCalendarShareService service = service();
    if (service == null || identityId <= 0) {
      return Map.of();
    }
    try {
      Map<Long, CalendarShareLevel> levels = service.getShareLevels(identityId);
      return levels == null ? Map.of() : levels;
    } catch (RuntimeException | LinkageError e) {
      LOG.warn("The calendars shared with identity {} could not be read; none is read", identityId, e);
      return Map.of();
    }
  }

  /**
   * The calendars shared with an identity.
   *
   * @param identityId identity identifier of the reader
   * @return the calendar identifiers, empty when none or when they cannot be read
   */
  public List<Long> getSharedCalendarIds(long identityId) {
    AgendaCalendarShareService service = service();
    if (service == null || identityId <= 0) {
      return List.of();
    }
    try {
      List<Long> ids = service.getSharedCalendarIds(identityId);
      return ids == null ? List.of() : ids;
    } catch (RuntimeException | LinkageError e) {
      LOG.warn("The calendars shared with identity {} could not be read; none is read", identityId, e);
      return List.of();
    }
  }

  /**
   * The share service, resolved from the container on first use and kept once
   * found. Not kept when absent: the Spring context may still be booting.
   *
   * @return the service, or null when it cannot be reached
   */
  private AgendaCalendarShareService service() {
    if (calendarShareService != null) {
      return calendarShareService;
    }
    try {
      calendarShareService = ExoContainerContext.getService(AgendaCalendarShareService.class);
    } catch (RuntimeException | LinkageError e) {
      LOG.debug("The calendar share service cannot be reached yet; calendars are read as not shared", e);
      return null;
    }
    return calendarShareService;
  }

}
