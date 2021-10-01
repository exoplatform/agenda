// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.service;

import static org.junit.Assert.*;

import java.util.Collections;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import org.exoplatform.agenda.constant.ReminderPeriodType;
import org.exoplatform.agenda.model.*;

public class AgendaUserSettingsServiceTest extends BaseAgendaEventTest {

  @Test
  public void testDefaultSettings() throws Exception { // NOSONAR
    AgendaUserSettings agendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(56111l);
    assertNotNull(agendaUserSettings);
    assertNotNull(agendaUserSettings.getRemoteProviders());
    assertNotNull(agendaUserSettings.getAgendaDefaultView());
    assertNotNull(agendaUserSettings.getAgendaWeekStartOn());
    assertTrue(StringUtils.isBlank(agendaUserSettings.getConnectedRemoteProvider()));
    assertTrue(StringUtils.isBlank(agendaUserSettings.getConnectedRemoteUserId()));
    assertTrue(StringUtils.isBlank(agendaUserSettings.getTimeZoneId()));
    assertNotNull(agendaUserSettings.getWorkingTimeEnd());
    assertNotNull(agendaUserSettings.getWorkingTimeStart());
  }

  @Test
  public void testSaveUserSettings() throws Exception { // NOSONAR
    long identityId = 2223l;
    AgendaUserSettings agendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(identityId);

    agendaUserSettings.setAgendaDefaultView("agendaDefaultView");
    agendaUserSettings.setAgendaWeekStartOn("agendaWeekStartOn");
    agendaUserSettings.setConnectedRemoteProvider("connectedRemoteProvider");
    agendaUserSettings.setConnectedRemoteUserId("connectedRemoteUserId");
    agendaUserSettings.setReminders(Collections.singletonList(new EventReminderParameter(5, ReminderPeriodType.DAY)));
    agendaUserSettings.setShowWorkingTime(false);
    agendaUserSettings.setWorkingTimeEnd("workingTimeEnd");
    agendaUserSettings.setWorkingTimeStart("workingTimeStart");
    agendaUserSettings.setTimeZoneId("timeZoneId");

    agendaUserSettingsService.saveAgendaUserSettings(identityId, agendaUserSettings);

    AgendaUserSettings storedAgendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(identityId);
    assertEquals(agendaUserSettings.getAgendaDefaultView(), storedAgendaUserSettings.getAgendaDefaultView());
    assertEquals(agendaUserSettings.getAgendaWeekStartOn(), storedAgendaUserSettings.getAgendaWeekStartOn());
    assertEquals(agendaUserSettings.getConnectedRemoteProvider(), storedAgendaUserSettings.getConnectedRemoteProvider());
    assertEquals(agendaUserSettings.getConnectedRemoteUserId(), storedAgendaUserSettings.getConnectedRemoteUserId());
    assertEquals(agendaUserSettings.getReminders(), storedAgendaUserSettings.getReminders());
    assertEquals(agendaUserSettings.getTimeZoneId(), storedAgendaUserSettings.getTimeZoneId());
    assertEquals(agendaUserSettings.getTimeZoneId(), storedAgendaUserSettings.getTimeZoneId());
    assertEquals(agendaUserSettings.getWorkingTimeEnd(), storedAgendaUserSettings.getWorkingTimeEnd());
    assertEquals(agendaUserSettings.getWorkingTimeStart(), storedAgendaUserSettings.getWorkingTimeStart());
  }

  @Test
  public void testSaveUserConnector() throws Exception { // NOSONAR
    long identityId = 2223l;

    RemoteProvider remoteProvider = new RemoteProvider(0, "connectorName", "Client API Key", true);
    remoteProvider = agendaRemoteEventService.saveRemoteProvider(remoteProvider);
    assertNotNull(remoteProvider);

    agendaUserSettingsService.saveUserConnector("connectorName", "connectorUserId", identityId);
    AgendaUserSettings agendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(identityId);

    assertEquals("connectorName", agendaUserSettings.getConnectedRemoteProvider());
    assertEquals("connectorUserId", agendaUserSettings.getConnectedRemoteUserId());

    remoteProvider.setEnabled(false);
    agendaRemoteEventService.saveRemoteProvider(remoteProvider);
    try {
      agendaUserSettingsService.saveUserConnector("connectorName", "connectorUserId", identityId);
      fail();
    } catch (Exception e) {
      // Expected
    }
  }

}
