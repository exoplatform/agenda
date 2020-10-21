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
package org.exoplatform.agenda.search;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.service.*;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.commons.search.domain.Document;
import org.exoplatform.commons.search.index.impl.ElasticIndexingServiceConnector;
import org.exoplatform.commons.utils.HTMLSanitizer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class AgendaIndexingServiceConnector extends ElasticIndexingServiceConnector {
  private static final long           serialVersionUID = 3299213985533395198L;

  public static final String          TYPE             = "event";

  private static final Log            LOG              = ExoLogger.getLogger(AgendaIndexingServiceConnector.class);

  private final AgendaCalendarService agendaCalendarService;                                                       // NOSONAR

  private final AgendaEventService    agendaEventService;                                                          // NOSONAR

  private AgendaEventAttendeeService  attendeeService;                                                             // NOSONAR

  public AgendaIndexingServiceConnector(AgendaCalendarService agendaCalendarService,
                                        AgendaEventService agendaEventService,
                                        AgendaEventAttendeeService attendeeService,
                                        InitParams initParams) {
    super(initParams);

    this.agendaCalendarService = agendaCalendarService;
    this.agendaEventService = agendaEventService;
    this.attendeeService = attendeeService;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public Document create(String id) {
    return getDocument(id);
  }

  @Override
  public Document update(String id) {
    return getDocument(id);
  }

  @Override
  public List<String> getAllIds(int offset, int limit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean canReindex() {
    return false;
  }

  private Document getDocument(String id) {
    if (StringUtils.isBlank(id)) {
      throw new IllegalArgumentException("id is mandatory");
    }
    LOG.debug("Index document for agenda id={}", id);

    Event event = agendaEventService.getEventById(Long.parseLong(id));
    if (event == null) {
      throw new IllegalStateException("event with id '" + id + "' not found");
    }

    Map<String, String> fields = new HashMap<>();
    fields.put("id", Long.toString(event.getId()));

    List<Long> eventAttendees = attendeeService.getEventAttendees(event.getId())
                                               .stream()
                                               .map(EventAttendee::getIdentityId)
                                               .collect(Collectors.toList());
    if (!eventAttendees.isEmpty()) {
      fields.put("attendee", String.valueOf(eventAttendees));
    }

    if (event.getParentId() > 0) {
      fields.put("parentId", Long.toString(event.getParentId()));
    }

    long calendarId = event.getCalendarId();
    fields.put("calendarId", String.valueOf(calendarId));

    Calendar calendar = agendaCalendarService.getCalendarById(calendarId);
    long ownerIdentityId = calendar.getOwnerId();

    fields.put("ownerId", String.valueOf(ownerIdentityId));

    if (event.getOccurrence() != null) {
      fields.put("occurrenceId", Long.toString(event.getOccurrence().getId().toInstant().getEpochSecond()));
    }

    ZonedDateTime start = event.getStart();
    if (start != null) {
      fields.put("startTime", toMillisecondsString(start));
    }

    ZonedDateTime end = event.getEnd();
    if (end != null) {
      fields.put("endTime", toMillisecondsString(end));
    }

    if (StringUtils.isNotEmpty(event.getLocation())) {
      fields.put("location", event.getLocation());
    }

    String summary = null;
    if (StringUtils.isNotBlank(event.getSummary())) {
      summary = event.getSummary();
      fields.put("summary", summary);
    }

    String description = null;
    if (StringUtils.isNotBlank(event.getDescription())) {
      description = event.getDescription();
      fields.put("description", description);
    }

    Set<String> eventPermissionIds = eventAttendees.stream().map(String::valueOf).collect(Collectors.toSet());
    eventPermissionIds.add(String.valueOf(ownerIdentityId));

    ZonedDateTime lastUpdateDateTime = event.getUpdated() == null ? event.getCreated() : event.getUpdated();
    Document document = new Document(TYPE, id, null, AgendaDateUtils.toDate(lastUpdateDateTime), eventPermissionIds, fields);

    // Ensure to index text only without html tags
    if (StringUtils.isNotEmpty(String.valueOf(description))) {
      description = document.getFields().get("description");
      if (StringUtils.isNotBlank(description)) {
        description = StringEscapeUtils.unescapeHtml(description);
        try {
          description = HTMLSanitizer.sanitize(description);
        } catch (Exception e) {
          LOG.warn("Error sanitizing activity '{}' body", event.getId());
        }
        description = htmlToText(description);
        document.addField("description", description);
      }
    }
    return document;
  }

  private String toMillisecondsString(ZonedDateTime dateTime) {
    return String.valueOf(dateTime.withZoneSameInstant(ZoneOffset.UTC).toEpochSecond() * 1000);
  }

  private String htmlToText(String source) {
    source = source.replaceAll("<( )*head([^>])*>", "<head>");
    source = source.replaceAll("(<( )*(/)( )*head( )*>)", "</head>");
    source = source.replaceAll("(<head>).*(</head>)", "");
    source = source.replaceAll("<( )*script([^>])*>", "<script>");
    source = source.replaceAll("(<( )*(/)( )*script( )*>)", "</script>");
    source = source.replaceAll("(<script>).*(</script>)", "");
    source = source.replaceAll("javascript:", "");
    source = source.replaceAll("<( )*style([^>])*>", "<style>");
    source = source.replaceAll("(<( )*(/)( )*style( )*>)", "</style>");
    source = source.replaceAll("(<style>).*(</style>)", "");
    source = source.replaceAll("<( )*td([^>])*>", "\t");
    source = source.replaceAll("<( )*br( )*(/)*>", "\n");
    source = source.replaceAll("<( )*li( )*>", "\n");
    source = source.replaceAll("<( )*div([^>])*>", "\n");
    source = source.replaceAll("<( )*tr([^>])*>", "\n");
    source = source.replaceAll("<( )*p([^>])*>", "\n");
    source = source.replaceAll("<[^>]*>", "");
    return source;
  }

}
