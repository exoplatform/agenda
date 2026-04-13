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
package org.exoplatform.agenda.listener;

import io.meeds.content.news.service.NewsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataObject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.exoplatform.agenda.util.Utils.*;

@Asynchronous
@Component
@RequiredArgsConstructor
public class AgendaEventMetadataListener extends Listener<AgendaEventModification, Object> {

  private static final String[] LISTENER_EVENTS = { POST_DELETE_AGENDA_EVENT_EVENT };

  private final MetadataService metadataService;

  private final ListenerService listenerService;

  private final NewsService newsService;

  @PostConstruct
  public void init() {
    for (String listener : LISTENER_EVENTS) {
      listenerService.addListener(listener, this);
    }
  }

  @Override
  public void onEvent(Event<AgendaEventModification, Object> event) throws Exception {
    AgendaEventModification agendaEventModification = event.getSource();
    Long eventId = agendaEventModification.getEventId();
    MetadataObject metadataObject = new MetadataObject(EVENT_METADATA_NAME, String.valueOf(eventId));
    List<MetadataItem> metadataItems =
        metadataService.getMetadataItemsByMetadataAndObject(EVENT_METADATA_KEY, metadataObject);
    if (CollectionUtils.isNotEmpty(metadataItems)) {
      MetadataItem metadataItem = metadataItems.getFirst();
      Map<String, String> properties = metadataItem.getProperties();
      if (properties.containsKey(CONTENT_ID)) {
        String contentId = properties.get(CONTENT_ID);
        newsService.removeArticleMetadataProperty(contentId, EVENT_ID, agendaEventModification.getModifierId());
        metadataService.deleteMetadataItemsByObject(metadataObject);
      }
    }
  }
}
