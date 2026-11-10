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
package org.exoplatform.agenda.listener.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.listener.Event;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.utils.AnalyticsUtils;

public class AgendaSavedEventListenerTest {

  private static final String               CREATED_EVENT     = "exo.agenda.event.created";

  private static final String               UPDATED_EVENT     = "exo.agenda.event.updated";

  private static final long                 EVENT_ID          = 12L;

  private static final long                 CALENDAR_ID       = 3L;

  private static final long                 CALENDAR_OWNER_ID = 5L;

  private static final long                 USER_ID           = 7L;

  private MockedStatic<ExoContainerContext> containerContext;

  private MockedStatic<AnalyticsUtils>      analyticsUtils;

  private AgendaEventService                agendaEventService;

  @BeforeEach
  public void setUp() throws Exception {
    agendaEventService = mock(AgendaEventService.class);
    AgendaCalendarService agendaCalendarService = mock(AgendaCalendarService.class);
    IdentityManager identityManager = mock(IdentityManager.class);

    org.exoplatform.agenda.model.Event agendaEvent = mock(org.exoplatform.agenda.model.Event.class);
    when(agendaEvent.getId()).thenReturn(EVENT_ID);
    when(agendaEvent.getCalendarId()).thenReturn(CALENDAR_ID);
    when(agendaEvent.getCreatorId()).thenReturn(USER_ID);
    when(agendaEvent.getModifierId()).thenReturn(USER_ID);
    when(agendaEvent.getStatus()).thenReturn(EventStatus.CONFIRMED);
    when(agendaEventService.getEventById(EVENT_ID)).thenReturn(agendaEvent);

    Calendar calendar = mock(Calendar.class);
    when(calendar.getOwnerId()).thenReturn(CALENDAR_OWNER_ID);
    when(agendaCalendarService.getCalendarById(CALENDAR_ID)).thenReturn(calendar);

    Identity ownerIdentity = mock(Identity.class);
    when(ownerIdentity.getProviderId()).thenReturn(OrganizationIdentityProvider.NAME);
    when(identityManager.getIdentity(String.valueOf(CALENDAR_OWNER_ID))).thenReturn(ownerIdentity);

    containerContext = mockStatic(ExoContainerContext.class);
    containerContext.when(() -> ExoContainerContext.getService(AgendaEventService.class)).thenReturn(agendaEventService);
    containerContext.when(() -> ExoContainerContext.getService(AgendaCalendarService.class)).thenReturn(agendaCalendarService);
    containerContext.when(() -> ExoContainerContext.getService(IdentityManager.class)).thenReturn(identityManager);
    analyticsUtils = mockStatic(AnalyticsUtils.class);
  }

  @AfterEach
  public void tearDown() {
    analyticsUtils.close();
    containerContext.close();
  }

  @Test
  public void aCreatedEventIsRecordedAsACreation() throws Exception {
    new AgendaSavedEventListener().onEvent(new Event<>(CREATED_EVENT, modification(AgendaEventModificationType.ADDED), null));

    assertEquals(AgendaSavedEventListener.EVENT_CREATED_OPERATION_NAME, recordedOperation());
  }

  @Test
  public void theContinuationOfASplitSeriesIsNotRecorded() throws Exception {
    new AgendaSavedEventListener().onEvent(new Event<>(CREATED_EVENT,
                                                       modification(AgendaEventModificationType.ADDED,
                                                                    AgendaEventModificationType.SPLIT),
                                                       null));

    analyticsUtils.verify(() -> AnalyticsUtils.addStatisticData(any()), never());
    verify(agendaEventService, never()).getEventById(anyLong());
  }

  @Test
  public void theShortenedSeriesOfASplitIsRecordedAsAnUpdate() throws Exception {
    new AgendaSavedEventListener().onEvent(new Event<>(UPDATED_EVENT, modification(AgendaEventModificationType.UPDATED), null));

    assertEquals(AgendaSavedEventListener.EVENT_UPDATED_OPERATION_NAME, recordedOperation());
  }

  private AgendaEventModification modification(AgendaEventModificationType... types) {
    AgendaEventModification modification = new AgendaEventModification(EVENT_ID, CALENDAR_ID, USER_ID);
    for (AgendaEventModificationType type : types) {
      modification.addModificationType(type);
    }
    return modification;
  }

  private String recordedOperation() {
    ArgumentCaptor<StatisticData> statistic = ArgumentCaptor.forClass(StatisticData.class);
    analyticsUtils.verify(() -> AnalyticsUtils.addStatisticData(statistic.capture()));
    return statistic.getValue().getOperation();
  }

}
