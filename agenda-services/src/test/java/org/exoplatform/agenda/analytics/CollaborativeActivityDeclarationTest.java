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
package org.exoplatform.agenda.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.listener.analytics.AgendaEventResponseListener;
import org.exoplatform.agenda.listener.analytics.AgendaSavedEventListener;
import org.exoplatform.agenda.service.AgendaCalendarSubscriptionServiceImpl;

import tools.jackson.databind.ObjectMapper;

/**
 * The collaborative activity declaration analytics reads from this jar:
 * creating and updating an event and responding to an invitation count; the
 * announcement posted in a space when a calendar is subscribed is excluded by
 * its type. The strings are read from the constants of the code that emits
 * them; the label keys are matched by hand against agenda-webapps'
 * Portlets_en.properties.
 */
class CollaborativeActivityDeclarationTest {

  private static final String DECLARATION_FILE = "analytics-collaborative-activities.json";

  @Test
  @SuppressWarnings("unchecked")
  void theDeclarationCountsTheEventActionsAndExcludesTheSubscriptionAnnouncement() throws IOException {
    Map<String, Object> declaration;
    try (InputStream content = getClass().getClassLoader().getResourceAsStream(DECLARATION_FILE)) {
      assertNotNull(content, "the declaration ships at the root of agenda-services, which lands in /lib");
      declaration = new ObjectMapper().readValue(content, Map.class);
    }

    assertEquals(List.of("agenda"), declaration.get("modules"));
    assertEquals(List.of(AgendaSavedEventListener.EVENT_CREATED_OPERATION_NAME,
                         AgendaSavedEventListener.EVENT_UPDATED_OPERATION_NAME,
                         AgendaEventResponseListener.EVENT_RESPONSE),
                 declaration.get("operations"));
    assertEquals(List.of(AgendaCalendarSubscriptionServiceImpl.CALENDAR_SUBSCRIPTION_ACTIVITY_TYPE),
                 declaration.get("excludedActivityTypes"));
    assertFalse(declaration.containsKey("legacyOperations"));
    assertFalse(declaration.containsKey("browserOperations"));

    Map<String, Object> labels = (Map<String, Object>) declaration.get("labels");
    assertEquals(List.of("agenda.collaborativeActivity.help.counted.createEvent",
                         "agenda.collaborativeActivity.help.counted.updateEvent",
                         "agenda.collaborativeActivity.help.counted.respondEvent"),
                 labels.get("counted"));
    assertEquals(List.of("agenda.collaborativeActivity.help.notCounted.subscribeCalendar",
                         "agenda.collaborativeActivity.help.notCounted.createDatePoll"),
                 labels.get("notCounted"));
  }

}
