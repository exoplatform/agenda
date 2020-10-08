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

import java.util.*;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.EntityBuilder;
import org.exoplatform.commons.search.domain.Document;
import org.exoplatform.commons.search.index.impl.ElasticIndexingServiceConnector;
import org.exoplatform.commons.utils.HTMLSanitizer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.manager.IdentityManager;

public class AgendaIndexingServiceConnector extends ElasticIndexingServiceConnector {
  private static final long           serialVersionUID = 3299213985533395198L;

  public static final String          TYPE             = "event";

  private static final Log            LOG              = ExoLogger.getLogger(AgendaIndexingServiceConnector.class);

  private final AgendaCalendarService agendaCalendarService;

  private final AgendaEventService    agendaEventService;

  private AgendaEventAttendeeService  attendeeService;

  private final IdentityManager       identityManager;

  public AgendaIndexingServiceConnector(AgendaCalendarService agendaCalendarService,
                                        IdentityManager identityManager,
                                        AgendaEventService agendaEventService,
                                        AgendaEventAttendeeService attendeeService,
                                        InitParams initParams) {
    super(initParams);

    this.agendaCalendarService = agendaCalendarService;
    this.identityManager = identityManager;
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

    if (event.getParentId() > 0) {
      fields.put("parentId", Long.toString(event.getParentId()));
    }
    long ownerIdentityId = 0;
    if (event.getCalendarId() > 0) {
      ownerIdentityId = event.getCalendarId();
      fields.put("ownerId", Long.toString(ownerIdentityId));
    }
    Boolean isRecurrent = event.getRecurrence() == null;
    fields.put("isRecurrent", isRecurrent.toString());
    List<EventAttendee> eventAttendee = attendeeService.getEventAttendees(event.getId());
    if (eventAttendee != null) {
      fields.put("attendee", String.valueOf(eventAttendee));
    }

    if (event.getStart() != null) {
      fields.put("startTime", Long.toString(event.getStart().toInstant().getEpochSecond()));
    }
    if (event.getEnd() != null) {
      fields.put("endTime", Long.toString(event.getEnd().toInstant().getEpochSecond()));
    }
    if (StringUtils.isNotEmpty(event.getLocation())) {
      fields.put("location", event.getLocation());
    }

    String suammry = null;
    if (StringUtils.isNotBlank(event.getSummary())) {
      suammry = event.getSummary();
      fields.put("suammry", suammry);
    }
    String description = null;
    if (StringUtils.isNotBlank(event.getDescription())) {
      description = event.getDescription();
      fields.put("description", description);
    }

    Document document = new Document(TYPE,
                                     id,
                                     null,
                                     Date.from(event.getCreated().toInstant()),
                                     Collections.singleton(Long.toString(ownerIdentityId)),
                                     fields);

    /*StringBuilder eventContent = new StringBuilder(document.getFields().get("body"));
    if (StringUtils.isNotBlank(event.getDescription())) {
      eventContent.append("\n");
      eventContent.append(event.getDescription());
    }
    document.addField("body", String.valueOf(eventContent));
*/
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
