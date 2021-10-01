// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.service;

import static org.junit.Assert.*;

import java.time.ZonedDateTime;
import java.util.List;

import org.apache.commons.codec.binary.StringUtils;
import org.junit.Test;

import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.plugin.RemoteProviderDefinitionPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;

public class AgendaRemoteEventServiceTest extends BaseAgendaEventTest {

  @Test
  public void testAddRemoteProviders() throws Exception { // NOSONAR
    InitParams params = new InitParams();
    ValueParam param = new ValueParam();
    param.setName("connectorName");
    param.setValue("remoteProviderTest");
    params.addParameter(param);

    param = new ValueParam();
    param.setName("connectorEnabled");
    param.setValue("false");
    params.addParameter(param);
    RemoteProviderDefinitionPlugin plugin = new RemoteProviderDefinitionPlugin(params);

    RemoteProvider addedRemoteProvider = agendaRemoteEventService.saveRemoteProviderPlugin(plugin);
    assertNotNull(addedRemoteProvider);
    assertTrue(addedRemoteProvider.getId() > 0);
    assertFalse(addedRemoteProvider.isEnabled());
    assertEquals("remoteProviderTest", addedRemoteProvider.getName());
  }

  @Test
  public void testGetAndSaveRemoteProviders() throws Exception { // NOSONAR
    List<RemoteProvider> remoteProviders = agendaRemoteEventService.getRemoteProviders();
    assertNotNull(remoteProviders);
    int initialisize = remoteProviders.size();

    RemoteProvider remoteProviderToSave = new RemoteProvider(0, "testProvider", "Client API Key", false);
    RemoteProvider remoteProviderSaved = agendaRemoteEventService.saveRemoteProvider(remoteProviderToSave);
    assertNotNull(remoteProviderSaved);
    assertTrue(remoteProviderSaved.getId() > 0);
    assertEquals(remoteProviderToSave.getName(), remoteProviderSaved.getName());
    assertEquals(remoteProviderToSave.isEnabled(), remoteProviderSaved.isEnabled());

    remoteProviders = agendaRemoteEventService.getRemoteProviders();
    assertNotNull(remoteProviders);
    assertEquals(initialisize + 1l, remoteProviders.size());
  }

  @Test
  public void testSaveRemoteProviderStatus() throws Exception { // NOSONAR
    RemoteProvider remoteProviderToSave = new RemoteProvider(0, "testProvider222", "Client API Key", false);
    RemoteProvider remoteProviderSaved = agendaRemoteEventService.saveRemoteProvider(remoteProviderToSave);
    assertNotNull(remoteProviderSaved);
    assertFalse(remoteProviderSaved.isEnabled());

    agendaRemoteEventService.saveRemoteProviderStatus(remoteProviderSaved.getName(), false);
    RemoteProvider remoteProvider = agendaRemoteEventService.getRemoteProviders()
                                                            .stream()
                                                            .filter(provider -> StringUtils.equals(provider.getName(),
                                                                                                   remoteProviderSaved.getName()))
                                                            .findFirst()
                                                            .orElse(null);
    assertNotNull(remoteProvider);
    assertFalse(remoteProvider.isEnabled()); // NOSONAR

    agendaRemoteEventService.saveRemoteProviderStatus(remoteProviderSaved.getName(), true);
    remoteProvider = agendaRemoteEventService.getRemoteProviders()
                                             .stream()
                                             .filter(provider -> StringUtils.equals(provider.getName(),
                                                                                    remoteProviderSaved.getName()))
                                             .findFirst()
                                             .orElse(null);
    assertNotNull(remoteProvider);
    assertTrue(remoteProvider.isEnabled()); // NOSONAR
  }

  @Test
  public void testSaveRemoteEvent() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = false;

    long identityId1 = Long.parseLong(testuser1Identity.getId());

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), identityId1, testuser5Identity);

    long eventId = event.getId();
    RemoteEvent remoteEvent = new RemoteEvent(0,
                                              eventId,
                                              identityId1,
                                              "remoteEventId",
                                              remoteProvider.getId(),
                                              remoteProvider.getName());

    RemoteEvent savedRemoteEvent = agendaRemoteEventService.saveRemoteEvent(eventId, remoteEvent, identityId1);
    assertNotNull(savedRemoteEvent);
    assertTrue(savedRemoteEvent.getId() > 0);
    assertEquals(remoteEvent.getEventId(), savedRemoteEvent.getEventId());
    assertEquals(remoteEvent.getIdentityId(), savedRemoteEvent.getIdentityId());
    assertEquals(remoteEvent.getRemoteId(), savedRemoteEvent.getRemoteId());
    assertEquals(remoteEvent.getRemoteProviderId(), savedRemoteEvent.getRemoteProviderId());
    assertEquals(remoteEvent.getRemoteProviderName(), savedRemoteEvent.getRemoteProviderName());

    agendaRemoteEventService.saveRemoteEvent(eventId, null, identityId1);

    RemoteEvent foundRemoteEvent = agendaRemoteEventService.findRemoteEvent(eventId, identityId1);
    assertNull(foundRemoteEvent);
  }

  @Test
  public void testFindRemoteEvent() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);
    boolean allDay = false;

    long identityId1 = Long.parseLong(testuser1Identity.getId());
    long identityId5 = Long.parseLong(testuser5Identity.getId());

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), identityId1, testuser5Identity);

    long eventId = event.getId();
    RemoteEvent remoteEvent1 = new RemoteEvent(0,
                                               eventId,
                                               identityId1,
                                               "remoteEventId",
                                               remoteProvider.getId(),
                                               remoteProvider.getName());

    agendaRemoteEventService.saveRemoteEvent(remoteEvent1);
    agendaRemoteEventService.saveRemoteEvent(eventId, remoteEvent1, identityId5);

    RemoteEvent foundRemoteEvent1 = agendaRemoteEventService.findRemoteEvent(eventId, identityId1);
    assertNotNull(foundRemoteEvent1);

    RemoteEvent foundRemoteEvent5 = agendaRemoteEventService.findRemoteEvent(eventId, identityId5);
    assertNotNull(foundRemoteEvent5);

    assertNotEquals(foundRemoteEvent1.getId(), foundRemoteEvent5.getId());
  }
}
