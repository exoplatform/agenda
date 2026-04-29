/*
 * Copyright (C) 2026 eXo Platform SAS.
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
package org.exoplatform.agenda.listener.content;

import io.meeds.content.news.model.News;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.manager.IdentityManager;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.meeds.content.news.utils.NewsUtils.UPDATE_NEWS;
import static org.exoplatform.agenda.util.Utils.EVENT_ID;

@Asynchronous
@Component
@RequiredArgsConstructor
public class ContentAgendaEventSyncListener extends Listener<String, News> {

  private final ListenerService listenerService;

  private final AgendaEventService agendaEventService;

  private final IdentityManager identityManager;

  @PostConstruct
  public void init() {
    listenerService.addListener(UPDATE_NEWS, this);
  }

  @Override
  public void onEvent(Event<String, News> event) throws Exception {
    News news = event.getData();
    Map<String, String> parameters = news.getParameters();
    if (parameters == null || !parameters.containsKey(EVENT_ID)) {
      return;
    }

    long eventId = Long.parseLong(parameters.get(EVENT_ID));
    org.exoplatform.agenda.model.Event agendaEvent = agendaEventService.getEventById(eventId);
    if (agendaEvent == null) {
      return;
    }

    if (agendaEvent.getSummary().equals(news.getTitle())) {
      return;
    }

    String authorRemoteId = news.getAuthor();
    long modifierId = identityManager.getOrCreateUserIdentity(authorRemoteId).getIdentityId();

    agendaEventService.updateEventFields(agendaEvent.getId(),
                                         Map.of("summary", List.of(news.getTitle())),
                                         false,
                                         false,
                                         modifierId);
  }
}
