/**
 * Copyright (C) 2026 eXo Platform SAS.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.portlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletConfig;
import javax.portlet.PortletException;
import javax.portlet.PortletPreferences;
import javax.portlet.RenderRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;

import io.meeds.social.cms.service.CMSService;
import io.meeds.social.portlet.CMSPortlet;

class AgendaTimelinePortletTest {

  private static final String           SETTING_NAME = "timeline-1";

  private static final Identity         IDENTITY     = new Identity("john");

  private MockedStatic<ExoContainerContext> containerContext;

  private CMSService                    cmsService;

  private PortletPreferences            preferences;

  private AgendaTimelinePortlet         portlet;

  @BeforeEach
  void setUp() throws PortletException {
    cmsService = mock(CMSService.class);
    containerContext = mockStatic(ExoContainerContext.class);
    containerContext.when(() -> ExoContainerContext.getService(CMSService.class)).thenReturn(cmsService);
    ConversationState.setCurrent(new ConversationState(IDENTITY));

    preferences = mock(PortletPreferences.class);
    when(preferences.getValue(CMSPortlet.NAME, null)).thenReturn(SETTING_NAME);

    PortletConfig config = mock(PortletConfig.class);
    when(config.getInitParameter("portlet-view-dispatched-file-path")).thenReturn("/WEB-INF/jsp/agendaTimeline.jsp");
    portlet = new AgendaTimelinePortlet();
    portlet.init(config);
  }

  @AfterEach
  void tearDown() {
    ConversationState.setCurrent(null);
    containerContext.close();
  }

  @Test
  void processActionRefusesUserWithoutEditPermission() throws Exception {
    ActionRequest request = actionRequest("{\"displayMode\":\"list\"}");

    assertThrows(PortletException.class, () -> portlet.processAction(request, mock(ActionResponse.class)));

    verify(cmsService).hasEditPermission(IDENTITY, "agendaTimeline", SETTING_NAME);
    verify(preferences, never()).setValue(anyString(), any());
    verify(preferences, never()).store();
  }

  @Test
  void processActionRefusesInstanceWithoutSettingName() throws Exception {
    when(preferences.getValue(CMSPortlet.NAME, null)).thenReturn(" ");
    ActionRequest request = actionRequest("{\"displayMode\":\"list\"}");

    assertThrows(PortletException.class, () -> portlet.processAction(request, mock(ActionResponse.class)));

    verifyNoInteractions(cmsService);
    verify(preferences, never()).store();
  }

  @Test
  void processActionRefusesSettingsThatAreNotAJsonObject() throws Exception {
    allowEdit();

    for (String settings : new String[] { null, "", "alert(document.cookie)", "[1]", "{\"displayMode\":" }) {
      ActionRequest request = actionRequest(settings);
      assertThrows(PortletException.class, () -> portlet.processAction(request, mock(ActionResponse.class)));
    }
    verify(preferences, never()).store();
  }

  @Test
  void processActionStoresOnlyKnownSettingsWithTheirExpectedType() throws Exception {
    allowEdit();
    ActionRequest request = actionRequest("""
        {"selectedSpaces":[{"identityId":"5","displayName":"Marketing"},"space:marketing"],
         "agendaSource":"selectedSpaces","customHeader":"true","displaySeeMore":true,
         "seeMoreUrl":"https://example.org/agenda","displayPending":false,"displayAddEvent":true,
         "agendaFilter":"allEvents","itemsNumber":"15","displayMode":"list",
         "canEdit":true,"name":"other-instance","agendaSourceExtra":"x"}
        """);

    portlet.processAction(request, mock(ActionResponse.class));

    verify(preferences).setValue("settings", """
        {"selectedSpaces":[{"identityId":"5","displayName":"Marketing"}],\
        "agendaSource":"selectedSpaces","displaySeeMore":true,\
        "seeMoreUrl":"https://example.org/agenda","displayPending":false,"displayAddEvent":true,\
        "agendaFilter":"allEvents","itemsNumber":15,"displayMode":"list"}""");
    verify(preferences).store();
  }

  @Test
  void processActionDropsUnknownValuesOfKnownSettings() throws Exception {
    allowEdit();
    ActionRequest request = actionRequest("""
        {"agendaSource":"mySpaces","agendaFilter":"<img>","displayMode":"grid","itemsNumber":"1e3","selectedSpaces":"all"}
        """);

    portlet.processAction(request, mock(ActionResponse.class));

    verify(preferences).setValue("settings", "{}");
  }

  @Test
  void seeMoreUrlKeepsWebMailAndPhoneLinksOnly() {
    assertEquals("{\"seeMoreUrl\":\"/portal/s/1/agenda\"}", seeMoreUrl("/portal/s/1/agenda"));
    assertEquals("{\"seeMoreUrl\":\"http://example.org\"}", seeMoreUrl("http://example.org"));
    assertEquals("{\"seeMoreUrl\":\"www.example.org\"}", seeMoreUrl("www.example.org"));
    assertEquals("{\"seeMoreUrl\":\"mailto:team@example.org\"}", seeMoreUrl("mailto:team@example.org"));
    assertEquals("{\"seeMoreUrl\":\"tel:+33100000000\"}", seeMoreUrl("tel:+33100000000"));
    assertEquals("{\"seeMoreUrl\":\"\"}", seeMoreUrl("javascript:alert(1)"));
    assertEquals("{\"seeMoreUrl\":\"\"}", seeMoreUrl(" JavaScript:alert(1)"));
    assertEquals("{\"seeMoreUrl\":\"\"}", seeMoreUrl("java\\tscript:alert(1)"));
    assertEquals("{\"seeMoreUrl\":\"\"}", seeMoreUrl("data:text/html,<script>alert(1)</script>"));
  }

  @Test
  void defaultSettingsOfThePortletDescriptorAreKept() {
    String defaultSettings = """
        {
        "selectedSpaces":[],
        "agendaSource":"",
        "customHeader":false,
        "displaySeeMore":true,
        "seeMoreUrl":"",
        "displayPending":true,
        "displayAddEvent": true,
        "agendaFilter": '',
        "itemsNumber": 10
        }
        """;

    assertEquals("{\"selectedSpaces\":[],\"agendaSource\":\"\",\"customHeader\":false,\"displaySeeMore\":true,"
        + "\"seeMoreUrl\":\"\",\"displayPending\":true,\"displayAddEvent\":true,\"agendaFilter\":\"\",\"itemsNumber\":10}",
                 AgendaTimelinePortlet.normalizeSettings(defaultSettings));
  }

  @Test
  void renderEscapesStoredSettingsForTheInlineScript() {
    RenderRequest request = renderRequest("""
        {"selectedSpaces":[{"displayName":"</script><script>alert(1)</script>&\u2028"}]}""");

    portlet.setViewRequestAttributes(SETTING_NAME, request, null);

    verify(request).setAttribute(AgendaTimelinePortlet.TIMELINE_SETTINGS,
                                 "{\"selectedSpaces\":[{\"displayName\":"
                                     + "\"\\u003c/script\\u003e\\u003cscript\\u003ealert(1)\\u003c/script\\u003e\\u0026\\u2028\"}]}");
  }

  @Test
  void renderReplacesStoredSettingsThatAreNotAJsonObject() {
    RenderRequest request = renderRequest("alert(document.cookie)");

    portlet.setViewRequestAttributes(SETTING_NAME, request, null);

    verify(request).setAttribute(AgendaTimelinePortlet.TIMELINE_SETTINGS, "{}");
  }

  @Test
  void renderWithoutStoredSettings() {
    RenderRequest request = renderRequest(null);

    portlet.setViewRequestAttributes(SETTING_NAME, request, null);

    verify(request).setAttribute(AgendaTimelinePortlet.TIMELINE_SETTINGS, "{}");
    assertNull(AgendaTimelinePortlet.normalizeSettings(null));
  }

  private void allowEdit() {
    when(cmsService.hasEditPermission(IDENTITY, "agendaTimeline", SETTING_NAME)).thenReturn(true);
  }

  private String seeMoreUrl(String url) {
    return AgendaTimelinePortlet.normalizeSettings("{\"seeMoreUrl\":\"" + url + "\"}");
  }

  private ActionRequest actionRequest(String settings) {
    ActionRequest request = mock(ActionRequest.class);
    when(request.getPreferences()).thenReturn(preferences);
    when(request.getParameter("settings")).thenReturn(settings);
    return request;
  }

  private RenderRequest renderRequest(String settings) {
    when(preferences.getValue("settings", null)).thenReturn(settings);
    RenderRequest request = mock(RenderRequest.class);
    when(request.getPreferences()).thenReturn(preferences);
    return request;
  }

}
