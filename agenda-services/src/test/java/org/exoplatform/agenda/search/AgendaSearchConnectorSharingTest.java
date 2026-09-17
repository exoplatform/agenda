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
package org.exoplatform.agenda.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.exoplatform.agenda.model.AgendaEventSearchFilter;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.commons.search.es.client.ElasticSearchingClient;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.PropertiesParam;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.commons.utils.ListAccess;

/**
 * Pins the shape of the statement the connector sends for a reader who has
 * calendars shared with them (EXO-90357), on the real query template: the
 * shared calendars are matched by identifier beside the reader's own
 * permissions, from the filter's server-set list only, and the statement is
 * still well-formed JSON with nothing shared.
 */
class AgendaSearchConnectorSharingTest {

  private static final long      READER = 7;

  private ElasticSearchingClient client;

  private AgendaSearchConnector  connector;

  /**
   * Builds the connector over the real template and a client that captures
   * the statement.
   *
   * @throws Exception when the template cannot be read
   */
  @BeforeEach
  void setUp() throws Exception {
    ConfigurationManager configurationManager = mock(ConfigurationManager.class);
    when(configurationManager.getInputStream(anyString()))
                                                          .thenAnswer(invocation -> getClass().getClassLoader()
                                                                                             .getResourceAsStream("agenda-search-query.json"));
    IdentityManager identityManager = mock(IdentityManager.class);
    Identity reader = new Identity(OrganizationIdentityProvider.NAME, "reader");
    reader.setId(String.valueOf(READER));
    when(identityManager.getIdentity(String.valueOf(READER))).thenReturn(reader);
    SpaceService spaceService = mock(SpaceService.class);
    @SuppressWarnings("unchecked")
    ListAccess<org.exoplatform.social.core.space.model.Space> noSpaces = mock(ListAccess.class);
    when(noSpaces.getSize()).thenReturn(0);
    when(noSpaces.load(0, 0)).thenReturn(new org.exoplatform.social.core.space.model.Space[0]);
    when(spaceService.getMemberSpaces("reader")).thenReturn(noSpaces);
    client = mock(ElasticSearchingClient.class);
    when(client.sendRequest(anyString(), eq("events"))).thenReturn("{\"hits\":{\"hits\":[]}}");
    InitParams initParams = new InitParams();
    PropertiesParam properties = new PropertiesParam();
    properties.setName("constructor.params");
    properties.setProperty("index", "events");
    initParams.addParam(properties);
    ValueParam path = new ValueParam();
    path.setName("query.file.path");
    path.setValue("agenda-search-query.json");
    initParams.addParam(path);
    PropertyManager.setProperty(PropertyManager.DEVELOPING, "false");
    PropertyManager.refresh();
    connector = new AgendaSearchConnector(configurationManager, identityManager, spaceService, mock(AgendaEventStorage.class), client, initParams);
  }

  /**
   * The shared calendars the service set reach the statement as a second
   * clause beside the permissions, one of the two having to match.
   *
   * @throws Exception when the statement is not JSON
   */
  @Test
  void theSharedCalendarsAreMatchedBesideThePermissions() throws Exception {
    AgendaEventSearchFilter filter = new AgendaEventSearchFilter(READER, ZoneOffset.UTC, "dentist", null, null, null, 0, 10);
    filter.setSharedCalendarIds(List.of(42L, 43L));

    connector.search(filter);

    JSONObject statement = statement();
    JSONObject bool = (JSONObject) ((JSONObject) ((JSONObject) statement.get("query")).get("bool")).get("filter");
    JSONObject filterBool = (JSONObject) bool.get("bool");
    JSONArray should = (JSONArray) filterBool.get("should");
    assertEquals(2, should.size());
    assertEquals(List.of(7L), ((JSONArray) ((JSONObject) ((JSONObject) should.get(0)).get("terms")).get("permissions")));
    assertEquals(List.of(42L, 43L), ((JSONArray) ((JSONObject) ((JSONObject) should.get(1)).get("terms")).get("calendarId")));
    assertEquals(1L, filterBool.get("minimum_should_match"));
  }

  /**
   * With nothing shared, the calendar clause is empty and the statement is
   * still well-formed: an empty terms clause matches nothing, and the
   * permissions clause carries the search as before.
   *
   * @throws Exception when the statement is not JSON
   */
  @Test
  void withNothingSharedTheStatementIsStillWellFormed() throws Exception {
    connector.search(new AgendaEventSearchFilter(READER, ZoneOffset.UTC, "dentist", null, null, null, 0, 10));

    JSONObject statement = statement();
    JSONObject filterBool = (JSONObject) ((JSONObject) ((JSONObject) ((JSONObject) statement.get("query")).get("bool")).get("filter")).get("bool");
    JSONArray should = (JSONArray) filterBool.get("should");
    assertTrue(((JSONArray) ((JSONObject) ((JSONObject) should.get(1)).get("terms")).get("calendarId")).isEmpty());
  }

  /**
   * The statement the client was sent, parsed.
   *
   * @return the statement
   * @throws Exception when it is not JSON
   */
  private JSONObject statement() throws Exception {
    ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
    verify(client).sendRequest(sent.capture(), eq("events"));
    return (JSONObject) new JSONParser().parse(sent.getValue());
  }

}
