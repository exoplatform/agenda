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
package org.exoplatform.agenda.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The space agenda's show-remote-events flag (EXO-90215) lives in the
 * settings blob beside the timeline's own. Two ways to lose it silently:
 * {@code clone()} goes through a positional constructor that does not carry
 * it, and the default settings are cloned for every user who never saved any;
 * and a blob written before the flag existed must read as off, since the
 * space's agenda shows the space's calendars alone unless the user asks.
 */
class AgendaUserSettingsSpaceAgendaFlagTest {

  @Test
  void testFlagIsOffByDefault() {
    assertFalse(new AgendaUserSettings().isShowRemoteEventsForSpaceAgenda());
  }

  @Test
  void testBlobWithoutTheFlagReadsAsOff() {
    AgendaUserSettings settings = AgendaUserSettings.fromString("{\"agendaDefaultView\":\"week\",\"showRemoteEventsForTimeLine\":true}");
    assertTrue(settings.isShowRemoteEventsForTimeLine());
    assertFalse(settings.isShowRemoteEventsForSpaceAgenda());
  }

  @Test
  void testFlagSurvivesTheStoredBlob() {
    AgendaUserSettings settings = new AgendaUserSettings();
    settings.setShowRemoteEventsForSpaceAgenda(true);
    assertTrue(AgendaUserSettings.fromString(settings.toString()).isShowRemoteEventsForSpaceAgenda());
  }

  /**
   * Both values, and each apart from the timeline's flag: a clone that
   * dropped the field would pass on false alone, and one that copied the
   * timeline's flag into it would pass whenever the two agree.
   */
  @Test
  void testCloneKeepsTheFlag() {
    AgendaUserSettings settings = new AgendaUserSettings();
    settings.setShowRemoteEventsForSpaceAgenda(true);
    settings.setShowRemoteEventsForTimeLine(false);
    AgendaUserSettings clone = settings.clone();
    assertTrue(clone.isShowRemoteEventsForSpaceAgenda());
    assertFalse(clone.isShowRemoteEventsForTimeLine());

    settings.setShowRemoteEventsForSpaceAgenda(false);
    settings.setShowRemoteEventsForTimeLine(true);
    clone = settings.clone();
    assertFalse(clone.isShowRemoteEventsForSpaceAgenda());
    assertTrue(clone.isShowRemoteEventsForTimeLine());
  }
}
