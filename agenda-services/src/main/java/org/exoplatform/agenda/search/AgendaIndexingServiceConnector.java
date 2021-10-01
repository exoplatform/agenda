// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.search;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.service.*;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.commons.search.domain.Document;
import org.exoplatform.commons.search.index.impl.ElasticIndexingServiceConnector;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class AgendaIndexingServiceConnector extends ElasticIndexingServiceConnector {

  public static final String          INDEX            = "event";

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
  public String getConnectorName() {
    return INDEX;
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
                                               .getEventAttendees()
                                               .stream()
                                               .map(EventAttendee::getIdentityId)
                                               .distinct()
                                               .collect(Collectors.toList());

    if (event.getParentId() > 0) {
      fields.put("parentId", Long.toString(event.getParentId()));
    }

    long calendarId = event.getCalendarId();
    fields.put("calendarId", String.valueOf(calendarId));

    Calendar calendar = agendaCalendarService.getCalendarById(calendarId);
    long ownerIdentityId = calendar.getOwnerId();

    fields.put("ownerId", String.valueOf(ownerIdentityId));

    if (event.getOccurrence() != null) {
      fields.put("occurrenceId", toMilliSecondsString(event.getOccurrence().getId()));
    }

    ZonedDateTime start = event.getStart();
    if (start != null) {
      fields.put("startTime", toMilliSecondsString(start));
    }

    ZonedDateTime end = event.getEnd();
    if (end != null) {
      fields.put("endTime", toMilliSecondsString(end));
    }

    if (StringUtils.isNotEmpty(event.getLocation())) {
      fields.put("location", event.getLocation());
    }

    String summary = null;
    if (StringUtils.isNotBlank(event.getSummary())) {
      summary = event.getSummary();
      fields.put("summary", summary);
    }

    if (event.getStatus() != null) {
      fields.put("status", event.getStatus().name());
    }

    String description = null;
    if (StringUtils.isNotBlank(event.getDescription())) {
      description = event.getDescription();
      fields.put("description", description);
    }

    Set<String> eventPermissionIds = eventAttendees.stream().map(String::valueOf).collect(Collectors.toSet());
    eventPermissionIds.add(String.valueOf(ownerIdentityId));

    ZonedDateTime lastUpdateDateTime = event.getUpdated() == null ? event.getCreated() : event.getUpdated();
    return new Document(id, null, AgendaDateUtils.toDate(lastUpdateDateTime), eventPermissionIds, fields);
  }

  private String toMilliSecondsString(ZonedDateTime dateTime) {
    return String.valueOf(dateTime.withZoneSameInstant(ZoneOffset.UTC).toInstant().toEpochMilli());
  }

}
