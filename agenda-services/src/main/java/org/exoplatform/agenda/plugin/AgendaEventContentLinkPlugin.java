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
package org.exoplatform.agenda.plugin;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.collections4.CollectionUtils;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventSearchResult;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.social.cms.model.ContentLinkExtension;
import io.meeds.social.cms.model.ContentLinkSearchResult;
import io.meeds.social.cms.plugin.ContentLinkPlugin;
import io.meeds.social.cms.service.ContentLinkPluginService;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;

@Component
public class AgendaEventContentLinkPlugin implements ContentLinkPlugin {

  public static final String                OBJECT_TYPE = AgendaEventAclPlugin.OBJECT_TYPE;

  private static final String               TITLE_KEY   = "contentLink.agendaEvent";

  private static final String               ICON        = "fa fa-calendar-day";

  private static final String               COMMAND     = "event";

  private static final ContentLinkExtension EXTENSION   = new ContentLinkExtension(OBJECT_TYPE,
                                                                                   TITLE_KEY,
                                                                                   ICON,
                                                                                   COMMAND,
                                                                                   true);

  @Autowired
  private PortalContainer                   container;

  @Autowired
  private UserACL                           userAcl;

  @Autowired
  private IdentityManager                   identityManager;

  @Autowired
  private AgendaEventService                agendaEventService;

  @PostConstruct
  public void init() {
    container.getComponentInstanceOfType(ContentLinkPluginService.class).addPlugin(this);
  }

  @Override
  public ContentLinkExtension getExtension() {
    return EXTENSION;
  }

  @Override
  @SneakyThrows
  public List<ContentLinkSearchResult> search(String keyword, Identity identity, Locale locale, int offset, int limit) {
    if (userAcl.isAnonymousUser(identity)) {
      return Collections.emptyList();
    }
    List<EventSearchResult> events = agendaEventService.search(getUserIdentityId(identity.getUserId()),
                                                               null,
                                                               keyword,
                                                               offset,
                                                               limit);
    return CollectionUtils.isEmpty(events) ? Collections.emptyList() :
                                           events.stream()
                                                 .map(this::toContentLink)
                                                 .toList();
  }

  @Override
  public String getContentTitle(String objectId, Locale locale) {
    Event event = agendaEventService.getEventById(Long.parseLong(objectId));
    return event == null ? null : event.getSummary();
  }

  private ContentLinkSearchResult toContentLink(EventSearchResult event) {
    if (event == null) {
      return null;
    } else {
      return new ContentLinkSearchResult(EXTENSION.getObjectType(),
                                         String.valueOf(event.getId()),
                                         Jsoup.parse(event.getSummary()).text(),
                                         EXTENSION.getIcon(),
                                         EXTENSION.isDrawer());
    }
  }

  private long getUserIdentityId(String username) {
    return Long.parseLong(identityManager.getOrCreateUserIdentity(username)
                                         .getId());
  }

}
