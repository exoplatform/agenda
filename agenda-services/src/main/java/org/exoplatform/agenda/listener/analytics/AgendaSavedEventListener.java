/*
 * Copyright (C) 2003-2022 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.listener.analytics;

import static org.exoplatform.analytics.utils.AnalyticsUtils.addSpaceStatistics;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.analytics.utils.AnalyticsUtils;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.listener.*;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

@Asynchronous
public class AgendaSavedEventListener extends Listener<AgendaEventModification, Object> {

  private static final String   EXO_AGENDA_EVENT_CREATED_EVENT_NAME = "exo.agenda.event.created";

  private static final String   EVENT_UPDATED_OPERATION_NAME        = "eventUpdated";

  private static final String   EVENT_CREATED_OPERATION_NAME        = "eventCreated";

  private AgendaCalendarService agendaCalendarService;

  private AgendaEventService    agendaEventService;

  private IdentityManager       identityManager;

  @Override
  public void onEvent(Event<AgendaEventModification, Object> event) throws Exception {
    AgendaEventModification eventModification = event.getSource();
    Long eventId = eventModification.getEventId();
    org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(eventId);
    boolean isNew = StringUtils.equals(event.getEventName(), EXO_AGENDA_EVENT_CREATED_EVENT_NAME);
    long userId = isNew ? agendaEvent.getCreatorId() : agendaEvent.getModifierId();
    addEventStatistic(agendaEvent, eventModification, userId, isNew);
  }

  private void addEventStatistic(org.exoplatform.agenda.model.Event event,
                                 AgendaEventModification eventModification,
                                 long userId,
                                 boolean isNew) {
    String operation = isNew ? EVENT_CREATED_OPERATION_NAME : EVENT_UPDATED_OPERATION_NAME;

    Calendar calendar = getAgendaCalendarService().getCalendarById(event.getCalendarId());
    long calendarOwnerId = calendar.getOwnerId();

    Identity spaceOwnerIdentity = getIdentityManager().getIdentity(String.valueOf(calendar.getOwnerId()));
    StatisticData statisticData = new StatisticData();
    if (StringUtils.equals(spaceOwnerIdentity.getProviderId(), SpaceIdentityProvider.NAME)) {
      SpaceService spaceService = CommonsUtils.getService(SpaceService.class);
      Space space = spaceService.getSpaceByPrettyName(spaceOwnerIdentity.getRemoteId());
      addSpaceStatistics(statisticData, space);
    } else {
      userId = calendarOwnerId;
    }

    statisticData.setModule("agenda");
    statisticData.setSubModule("event");
    statisticData.setOperation(operation);
    statisticData.setUserId(userId);
    statisticData.addParameter("eventId", event.getId());
    statisticData.addParameter("parentId", event.getParentId());
    statisticData.addParameter("calendarOwnerIdentityId", calendarOwnerId);
    statisticData.addParameter("creatorId", event.getCreatorId());
    statisticData.addParameter("modifierId", event.getModifierId());
    statisticData.addParameter("eventStatus", event.getStatus());
    statisticData.addParameter("isRecurrent", event.getRecurrence() != null);
    statisticData.addParameter("isExceptionalOccurrence", event.getOccurrence() != null);
    statisticData.addParameter("eventModificationTypes", eventModification.getModificationTypes());
    AnalyticsUtils.addStatisticData(statisticData);
  }

  public AgendaEventService getAgendaEventService() {
    if (agendaEventService == null) {
      agendaEventService = ExoContainerContext.getService(AgendaEventService.class);
    }
    return agendaEventService;
  }

  public AgendaCalendarService getAgendaCalendarService() {
    if (agendaCalendarService == null) {
      agendaCalendarService = ExoContainerContext.getService(AgendaCalendarService.class);
    }
    return agendaCalendarService;
  }

  public IdentityManager getIdentityManager() {
    if (identityManager == null) {
      identityManager = ExoContainerContext.getService(IdentityManager.class);
    }
    return identityManager;
  }
}
