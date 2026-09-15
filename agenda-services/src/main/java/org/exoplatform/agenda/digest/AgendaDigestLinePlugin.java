/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exoplatform.agenda.digest;

import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_EVENT_ID;
import static org.exoplatform.agenda.util.NotificationUtils.STORED_PARAMETER_MODIFIER_IDENTITY_ID;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.commons.digest.model.DigestItem;
import io.meeds.commons.digest.model.DigestLine;
import io.meeds.commons.digest.plugin.DigestLineContext;
import io.meeds.commons.digest.plugin.DigestLinePlugin;

/**
 * The digest email lines of the agenda notifications: an invitation to an
 * event, a new date poll. The event is read fresh from the stored event id and
 * its date is written in the recipient's timezone and language; a deleted
 * event gives no line.
 */
public class AgendaDigestLinePlugin extends DigestLinePlugin {

  public static final String  EVENT_ADDED_PLUGIN   = "EventAddedNotificationPlugin";

  public static final String  DATE_POLL_PLUGIN     = "DatePollNotificationPlugin";

  private static final String LINE_KEY_PREFIX      = "digest.line.";

  private static final String ALL_DAY_SUFFIX       = ".allDay";

  private AgendaEventService  agendaEventService;

  private IdentityManager     identityManager;

  public AgendaDigestLinePlugin(InitParams params) {
    super(params);
  }

  AgendaDigestLinePlugin(InitParams params, AgendaEventService agendaEventService, IdentityManager identityManager) {
    super(params);
    this.agendaEventService = agendaEventService;
    this.identityManager = identityManager;
  }

  @Override
  public DigestLine buildLine(DigestItem item, DigestLineContext context) {
    Event event = findEvent(item.getParam(STORED_PARAMETER_EVENT_ID));
    if (event == null) {
      return null;
    }
    String title = StringUtils.defaultString(event.getSummary());
    String url = eventUrl(event);
    return switch (item.getPluginId()) {
      case DATE_POLL_PLUGIN -> DigestLine.of(LINE_KEY_PREFIX + DATE_POLL_PLUGIN, title).withUrl(url);
      case EVENT_ADDED_PLUGIN -> invitationLine(item, context, event, title, url);
      default -> null;
    };
  }

  /** "{actor} invited you to "{title}" — {date} at {time}", date only for an all-day event */
  private DigestLine invitationLine(DigestItem item, DigestLineContext context, Event event, String title, String url) {
    String actor = fullName(item.getParam(STORED_PARAMETER_MODIFIER_IDENTITY_ID));
    DateTimeFormatter dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(context.getLocale());
    if (event.getStart() == null) {
      return DigestLine.of(LINE_KEY_PREFIX + EVENT_ADDED_PLUGIN + ALL_DAY_SUFFIX, actor, title, "").withUrl(url);
    }
    if (event.isAllDay()) {
      // An all-day event is a calendar day, the same whatever the recipient's
      // timezone: never converted, or a westward recipient reads the day before
      return DigestLine.of(LINE_KEY_PREFIX + EVENT_ADDED_PLUGIN + ALL_DAY_SUFFIX, actor, title, dateFormat.format(event.getStart().toLocalDate()))
                       .withUrl(url);
    }
    ZonedDateTime start = event.getStart().withZoneSameInstant(context.getZoneId());
    String time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(context.getLocale()).format(start);
    return DigestLine.of(LINE_KEY_PREFIX + EVENT_ADDED_PLUGIN, actor, title, dateFormat.format(start), time).withUrl(url);
  }

  private Event findEvent(String eventId) {
    if (StringUtils.isBlank(eventId)) {
      return null;
    }
    try {
      return getAgendaEventService().getEventById(Long.parseLong(eventId));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  protected String eventUrl(Event event) {
    return NotificationUtils.getEventURL(event);
  }

  /** The actor is stored as a social identity id */
  private String fullName(String identityId) {
    if (StringUtils.isBlank(identityId)) {
      return "";
    }
    Identity identity = getIdentityManager().getIdentity(identityId);
    String fullName = identity == null || identity.getProfile() == null ? null : identity.getProfile().getFullName();
    return StringUtils.isBlank(fullName) ? "" : fullName;
  }

  private AgendaEventService getAgendaEventService() {
    if (agendaEventService == null) {
      agendaEventService = ExoContainerContext.getService(AgendaEventService.class);
    }
    return agendaEventService;
  }

  private IdentityManager getIdentityManager() {
    if (identityManager == null) {
      identityManager = ExoContainerContext.getService(IdentityManager.class);
    }
    return identityManager;
  }

}
