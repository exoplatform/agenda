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
import io.meeds.content.news.service.NewsService;
import io.meeds.content.news.utils.NewsUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
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
public class AgendaEventContentSyncListener extends Listener<AgendaEventModification, Object> {

  private final ListenerService listenerService;

  private final AgendaEventService agendaEventService;

  private final MetadataService metadataService;

  private final NewsService newsService;

  private final IdentityManager identityManager;

  @PostConstruct
  public void init() {
    listenerService.addListener(POST_UPDATE_AGENDA_EVENT_EVENT, this);
  }

  /**
   * Propagates the summary of an updated agenda event to the news article
   * linked to it, when such a link exists. The link is the {@code contentId}
   * property of the event's metadata item; that item exists for every event
   * saved through the agenda service, and social returns its properties map
   * as {@code null} (never as an empty map) when no property is stored, so
   * the map is null-checked before it is read.
   *
   * <p>
   * An event carrying no summary propagates nothing: {@code SUMMARY} is
   * nullable, and pushing it onto the article would replace a title the
   * article has with none.
   *
   * @param event the {@code POST_UPDATE_AGENDA_EVENT_EVENT} listener event
   *          carrying the {@link AgendaEventModification}
   * @throws Exception when the news article update fails
   */
  @Override
  public void onEvent(Event<AgendaEventModification, Object> event) throws Exception {
    AgendaEventModification agendaEventModification = event.getSource();
    long eventId = agendaEventModification.getEventId();

    MetadataObject metadataObject = new MetadataObject(EVENT_METADATA_NAME, String.valueOf(eventId));
    List<MetadataItem> metadataItems =
        metadataService.getMetadataItemsByMetadataAndObject(EVENT_METADATA_KEY, metadataObject);
    if (CollectionUtils.isEmpty(metadataItems)) {
      return;
    }

    Map<String, String> properties = metadataItems.getFirst().getProperties();
    if (properties == null || !properties.containsKey(CONTENT_ID)) {
      return;
    }

    String contentId = properties.get(CONTENT_ID);
    News news = newsService.getNewsArticleById(contentId);
    if (news == null) {
      return;
    }

    org.exoplatform.agenda.model.Event agendaEvent = agendaEventService.getEventById(eventId);
    if (agendaEvent == null) {
      return;
    }

    // An event whose summary is not set propagates nothing: the alternative is
    // writing a null title onto an article that has one, which is a worse
    // outcome than leaving the two out of step. SUMMARY is nullable in the
    // schema, and the rest of the repo already reads it as such.
    if (StringUtils.isNotBlank(agendaEvent.getSummary()) && !StringUtils.equals(agendaEvent.getSummary(), news.getTitle())) {
      news.setTitle(agendaEvent.getSummary());
      String updater = identityManager.getIdentity(agendaEventModification.getModifierId()).getRemoteId();
      newsService.updateNews(news,
                             updater,
                             false,
                             false,
                             "article",
                             NewsUtils.NewsUpdateType.CONTENT_AND_TITLE.name());
    }
  }
}
