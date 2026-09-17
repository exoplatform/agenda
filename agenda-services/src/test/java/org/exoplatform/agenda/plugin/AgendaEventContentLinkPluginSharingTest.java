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
package org.exoplatform.agenda.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.agenda.constant.EventAccess;
import org.exoplatform.agenda.constant.EventVisibility;
import org.exoplatform.agenda.model.EventSearchResult;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.social.cms.model.ContentLinkSearchResult;

/**
 * The "link to content" picker over the events a reader may search
 * (EXO-90357): a hit of a private event read through a calendar share alone
 * is masked — no title — and is left out rather than linked blank or made
 * to fail the whole picker; every other hit links by its title.
 */
@ExtendWith(MockitoExtension.class)
class AgendaEventContentLinkPluginSharingTest {

  @Mock
  private UserACL                     userAcl;

  @Mock
  private IdentityManager             identityManager;

  @Mock
  private AgendaEventService          agendaEventService;

  @InjectMocks
  private AgendaEventContentLinkPlugin plugin;

  /**
   * A masked hit is dropped; the readable hits around it link by title.
   */
  @Test
  void aMaskedHitIsLeftOutAndTheOthersLink() {
    org.exoplatform.social.core.identity.model.Identity alice = new org.exoplatform.social.core.identity.model.Identity("organization", "alice");
    alice.setId("3");
    when(identityManager.getOrCreateUserIdentity(anyString())).thenReturn(alice);
    when(userAcl.isAnonymousUser(any(Identity.class))).thenReturn(false);
    when(agendaEventService.search(any())).thenReturn(List.of(hit(1, "Dentist", EventVisibility.PRIVATE, EventAccess.SHARED),
                                                              hit(2, "Standup", EventVisibility.DEFAULT, EventAccess.SHARED),
                                                              hit(3, "Own <b>secret</b>", EventVisibility.PRIVATE, EventAccess.FULL)));

    List<ContentLinkSearchResult> links = plugin.search("s", new Identity("alice"), Locale.ENGLISH, 0, 10);

    assertEquals(List.of("2", "3"), links.stream().map(ContentLinkSearchResult::getObjectId).toList());
    assertEquals(List.of("Standup", "Own secret"), links.stream().map(ContentLinkSearchResult::getTitle).toList());
  }

  /**
   * Nothing readable links to nothing, without failing.
   */
  @Test
  void onlyMaskedHitsLinkNothing() {
    org.exoplatform.social.core.identity.model.Identity alice = new org.exoplatform.social.core.identity.model.Identity("organization", "alice");
    alice.setId("3");
    when(identityManager.getOrCreateUserIdentity(anyString())).thenReturn(alice);
    when(userAcl.isAnonymousUser(any(Identity.class))).thenReturn(false);
    when(agendaEventService.search(any())).thenReturn(List.of(hit(1, "Dentist", EventVisibility.PRIVATE, EventAccess.SHARED)));

    assertTrue(plugin.search("dent", new Identity("alice"), Locale.ENGLISH, 0, 10).isEmpty());
  }

  /**
   * A search hit as the service answers it, read with the given access.
   *
   * @param id the event
   * @param summary the stored title
   * @param visibility the stored visibility
   * @param access how the reader may read it
   * @return the hit, masked when it must be
   */
  private static EventSearchResult hit(long id, String summary, EventVisibility visibility, EventAccess access) {
    EventSearchResult hit = new EventSearchResult();
    hit.setId(id);
    hit.setSummary(summary);
    hit.setVisibility(visibility);
    Utils.maskForAccess(hit, access);
    return hit;
  }

}
