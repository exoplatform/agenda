/**
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.listener;

import static org.exoplatform.agenda.util.Utils.POST_CREATE_AGENDA_EVENT_EVENT;
import static org.exoplatform.agenda.util.Utils.POST_CREATE_AGENDA_EVENT_POLL;
import static org.exoplatform.agenda.util.Utils.POST_DELETE_AGENDA_EVENT_EVENT;
import static org.exoplatform.agenda.util.Utils.POST_UPDATE_AGENDA_EVENT_EVENT;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.plugin.AgendaEventAclPlugin;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerBase;
import org.exoplatform.services.listener.ListenerService;

import io.meeds.social.html.model.HtmlProcessorContext;
import io.meeds.social.html.utils.HtmlUtils;

import jakarta.annotation.PostConstruct;

@Asynchronous
@Component
public class AgendaContentLinkListener implements ListenerBase<AgendaEventModification, Object> {

  private static final List<String> EVENT_NAMES = List.of(POST_DELETE_AGENDA_EVENT_EVENT,
                                                          POST_CREATE_AGENDA_EVENT_EVENT,
                                                          POST_UPDATE_AGENDA_EVENT_EVENT,
                                                          POST_DELETE_AGENDA_EVENT_EVENT,
                                                          POST_CREATE_AGENDA_EVENT_POLL);

  @Autowired
  private PortalContainer           container;

  @Autowired
  private AgendaEventService        agendaEventService;

  @PostConstruct
  public void init() {
    EVENT_NAMES.forEach(n -> container.getComponentInstanceOfType(ListenerService.class).addListener(n, this));
  }

  @Override
  public void onEvent(Event<AgendaEventModification, Object> event) throws Exception {
    AgendaEventModification eventModifications = event.getSource();
    long eventId = eventModifications.getEventId();
    HtmlProcessorContext context = new HtmlProcessorContext(AgendaEventAclPlugin.OBJECT_TYPE, String.valueOf(eventId), null);
    org.exoplatform.agenda.model.Event agendaEvent = agendaEventService.getEventById(eventId);
    if (agendaEvent == null) {
      HtmlUtils.process("", context);
    } else {
      HtmlUtils.process(agendaEvent.getDescription(), context);
    }
  }

}
