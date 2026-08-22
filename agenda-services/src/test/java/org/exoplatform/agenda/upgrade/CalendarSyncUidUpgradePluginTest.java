/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exoplatform.agenda.upgrade;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.xml.InitParams;

/**
 * The migration that gives an anchor to calendars older than the column.
 *
 * <p>
 * What these pin is mostly what the migration must <i>not</i> do: never
 * overwrite an anchor, never run on a fresh install, never spin.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CalendarSyncUidUpgradePluginTest {

  @Mock
  private EntityManagerService         entityManagerService;

  @Mock
  private EntityManager                entityManager;

  @Mock
  private TypedQuery<Long>             selectQuery;

  @Mock
  private Query                        updateQuery;

  private CalendarSyncUidUpgradePlugin plugin;

  /**
   * A plugin wired onto a mocked entity manager.
   */
  @BeforeEach
  public void buildThePlugin() {
    when(entityManagerService.getEntityManager()).thenReturn(entityManager);
    lenient().when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(selectQuery);
    lenient().when(selectQuery.setMaxResults(org.mockito.ArgumentMatchers.anyInt())).thenReturn(selectQuery);
    lenient().when(entityManager.createQuery(anyString())).thenReturn(updateQuery);
    lenient().when(updateQuery.setParameter(anyString(), any())).thenReturn(updateQuery);
    plugin = new CalendarSyncUidUpgradePlugin(entityManagerService, mock(InitParams.class));
  }

  /**
   * A first installation has nothing older to repair.
   */
  @Test
  public void aFreshInstallationIsNotMigrated() {
    // Its calendars all come through the DAO that already mints anchors, so
    // there is nothing predating the column.
    assertFalse(plugin.shouldProceedToUpgrade("7.3.0", ""));
    assertFalse(plugin.shouldProceedToUpgrade("7.3.0", null));
    assertTrue(plugin.shouldProceedToUpgrade("7.3.0", "7.2.0"));
  }

  /**
   * Every calendar lacking an anchor is given one.
   */
  @Test
  public void everyCalendarWithoutAnAnchorIsGivenOne() {
    when(selectQuery.getResultList()).thenReturn(List.of(1L, 2L, 3L)).thenReturn(List.of());
    when(updateQuery.executeUpdate()).thenReturn(1);

    plugin.processUpgrade("7.2.0", "7.3.0");

    verify(updateQuery, times(3)).executeUpdate();
  }

  /**
   * Each calendar gets its own anchor.
   */
  @Test
  public void twoCalendarsNeverShareAnAnchor() {
    // An anchor is what a binding is made against; two calendars sharing one
    // would bind a remote collection to whichever was looked up first.
    when(selectQuery.getResultList()).thenReturn(List.of(1L, 2L)).thenReturn(List.of());
    when(updateQuery.executeUpdate()).thenReturn(1);
    java.util.Set<Object> anchors = new java.util.HashSet<>();
    when(updateQuery.setParameter(eq("syncUid"), any())).thenAnswer(invocation -> {
      anchors.add(invocation.getArgument(1));
      return updateQuery;
    });

    plugin.processUpgrade("7.2.0", "7.3.0");

    assertTrue(anchors.size() == 2, "each calendar must get its own anchor");
  }

  /**
   * Nothing to repair costs one query and no writes.
   */
  @Test
  public void aDatabaseWithNothingToRepairWritesNothing() {
    when(selectQuery.getResultList()).thenReturn(List.of());

    plugin.processUpgrade("7.2.0", "7.3.0");

    verify(updateQuery, never()).executeUpdate();
  }

  /**
   * A page that never empties stops the run rather than looping for ever.
   */
  @Test
  public void aPageThatCannotBeWrittenStopsRatherThanSpinning() {
    // If the write silently affects nothing — a row locked, a constraint, a
    // guard refusing — the same page comes back for ever. The rows are still
    // there to migrate at the next boot; spinning would cost this one.
    when(selectQuery.getResultList()).thenReturn(List.of(1L, 2L));
    when(updateQuery.executeUpdate()).thenReturn(0);

    assertDoesNotThrow(() -> plugin.processUpgrade("7.2.0", "7.3.0"));
  }

  /**
   * The write refuses a calendar that has since acquired an anchor.
   */
  @Test
  public void anAnchorAlreadyThereIsNeverOverwritten() {
    // The guard lives in the statement, not in the loop: a calendar that
    // acquired an anchor between the page being read and the row being written
    // must keep it. Overwriting one breaks every binding made against it,
    // which is worse than the gap this migration closes.
    when(selectQuery.getResultList()).thenReturn(List.of(1L)).thenReturn(List.of());
    when(updateQuery.executeUpdate()).thenReturn(1);

    plugin.processUpgrade("7.2.0", "7.3.0");

    org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(entityManager, times(1)).createQuery(sql.capture());
    assertTrue(sql.getValue().contains("c.syncUid IS NULL"),
               "the update must refuse a calendar that already carries an anchor");
  }

  /**
   * Only the calendars missing an anchor are read.
   */
  @Test
  public void onlyCalendarsMissingAnAnchorAreRead() {
    when(selectQuery.getResultList()).thenReturn(List.of());

    plugin.processUpgrade("7.2.0", "7.3.0");

    org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(entityManager).createQuery(sql.capture(), eq(Long.class));
    assertTrue(sql.getValue().contains("c.syncUid IS NULL"), "the selection must be narrowed in the query");
  }
}
