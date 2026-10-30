/*
 * Copyright (C) 2026 eXo Platform SAS.
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.security.Identity;

import io.meeds.social.cms.service.CMSService;

@ExtendWith(MockitoExtension.class)
class AgendaTimelineHeaderTranslationPluginTest {

  private static final String                   SETTING_NAME = "timeline-1";

  @Mock
  private CMSService                            cmsService;

  @Mock
  private UserACL                               userAcl;

  @InjectMocks
  private AgendaTimelineHeaderTranslationPlugin plugin;

  @Test
  void headerTitleIsEditableWithTheInstanceEditPermissionOnly() {
    Identity editor = new Identity("editor");
    Identity viewer = new Identity("viewer");
    when(userAcl.getUserIdentity("editor")).thenReturn(editor);
    when(userAcl.getUserIdentity("viewer")).thenReturn(viewer);
    when(cmsService.hasEditPermission(editor, "agendaTimeline", SETTING_NAME)).thenReturn(true);

    assertTrue(plugin.hasEditPermission(SETTING_NAME, "editor"));
    assertFalse(plugin.hasEditPermission(SETTING_NAME, "viewer"));
  }

  @Test
  void headerTitleIsNotEditableByAnUnknownUser() {
    assertFalse(plugin.hasEditPermission(SETTING_NAME, "unknown"));
    verifyNoInteractions(cmsService);
  }

}
