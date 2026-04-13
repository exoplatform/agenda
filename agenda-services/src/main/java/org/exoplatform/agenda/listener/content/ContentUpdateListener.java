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
import org.apache.commons.collections4.CollectionUtils;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static io.meeds.content.news.utils.NewsUtils.UPDATE_NEWS;
import static io.meeds.content.news.utils.NewsUtils.DELETE_NEWS;
import static org.exoplatform.agenda.util.Utils.*;

@Asynchronous
@Component
@RequiredArgsConstructor
public class ContentUpdateListener extends Listener<String, News> {

  private static final String[] LISTENER_EVENTS = { UPDATE_NEWS, DELETE_NEWS };

  private final ListenerService listenerService;

  private final AgendaEventService agendaEventService;

  private final MetadataService metadataService;

  @PostConstruct
  public void init() {
    for (String listener : LISTENER_EVENTS) {
      listenerService.addListener(listener, this);
    }
  }

  @Override
  public void onEvent(Event<String, News> event) throws Exception {
    News news = event.getData();
    if (event.getEventName().equalsIgnoreCase(UPDATE_NEWS)) {
      Map<String, String> parameters = news.getParameters();
      if (parameters == null || !parameters.containsKey(EVENT_ID)) {
        return;
      }
      long eventId = Long.parseLong(parameters.get(EVENT_ID));
      org.exoplatform.agenda.model.Event agendaEvent = agendaEventService.getEventById(eventId);
      if (agendaEvent != null) {
        MetadataObject metadataObject = new MetadataObject(EVENT_METADATA_NAME, String.valueOf(eventId));
        Map<String, String> properties = Map.of(CONTENT_ID, String.valueOf(news.getId()));
        List<MetadataItem> metadataItems =
            metadataService.getMetadataItemsByMetadataAndObject(EVENT_METADATA_KEY, metadataObject);
        if (CollectionUtils.isEmpty(metadataItems)) {
          metadataService.createMetadataItem(metadataObject, EVENT_METADATA_KEY, properties, agendaEvent.getCreatorId());
        } else {
          MetadataItem metadataItem = metadataItems.getFirst();
          Map<String, String> existingProperties = metadataItem.getProperties();
          if (!existingProperties.containsKey(CONTENT_ID)
              || !existingProperties.get(CONTENT_ID).equals(String.valueOf(news.getId()))) {
            existingProperties.putAll(properties);
            metadataItem.setProperties(existingProperties);
            metadataService.updateMetadataItem(metadataItem, agendaEvent.getModifierId());
          }
        }
      }
    }
  }
}
