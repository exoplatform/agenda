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
package org.exoplatform.agenda.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import jakarta.persistence.PersistenceException;

import org.exoplatform.agenda.dao.CalendarLinkDAO;
import org.exoplatform.agenda.entity.CalendarLinkEntity;
import org.exoplatform.agenda.model.CalendarLink;

/**
 * Pins how the storage writes a link when two managers create one for the same
 * calendar at the same moment: the engine refuses the second insert, and the
 * second write replaces the first rather than failing.
 */
class CalendarLinkStorageTest {

  private CalendarLinkDAO     dao;

  private CalendarLinkStorage storage;

  /**
   * Builds the storage over a mocked repository.
   */
  @BeforeEach
  void setUp() {
    dao = mock(CalendarLinkDAO.class);
    storage = new CalendarLinkStorage(dao);
    when(dao.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(dao.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  /**
   * A calendar with no link gets a new row, flushed so a refusal surfaces here.
   */
  @Test
  void aFirstLinkIsInsertedAndFlushed() {
    CalendarLink link = storage.save(42, 7, "a".repeat(64), new Date(1000));

    verify(dao).saveAndFlush(any());
    assertEquals(42, link.getCalendarId());
    assertEquals(7, link.getCreatorId());
    assertEquals(1000, link.getCreatedDate());
    assertFalse(link.isActive(), "active is the service's to compute");
  }

  /**
   * A link lost to a concurrent creation becomes an update of the winning row,
   * whichever way the refusal is reported.
   *
   * @param refusal the exception the insert fails with
   */
  private void aLostRaceBecomesAnUpdate(RuntimeException refusal) {
    CalendarLinkEntity winner = new CalendarLinkEntity();
    winner.setId(5L);
    winner.setCalendarId(42);
    winner.setCreatorId(7);
    winner.setTokenHash("a".repeat(64));
    when(dao.findByCalendarId(42)).thenReturn(null, winner);
    when(dao.saveAndFlush(any())).thenThrow(refusal);

    CalendarLink link = storage.save(42, 8, "b".repeat(64), new Date(2000));

    verify(dao).save(winner);
    assertEquals(8, link.getCreatorId(), "the later write wins, as a reset would");
    assertEquals("b".repeat(64), link.getTokenHash());
  }

  /**
   * The refusal as Spring reports it.
   */
  @Test
  void aLostRaceReportedBySpringBecomesAnUpdate() {
    aLostRaceBecomesAnUpdate(new DataIntegrityViolationException("UK_AGENDA_CALENDAR_LINK_CAL"));
  }

  /**
   * The refusal as JPA reports it, untranslated.
   */
  @Test
  void aLostRaceReportedByJpaBecomesAnUpdate() {
    aLostRaceBecomesAnUpdate(new PersistenceException("UK_AGENDA_CALENDAR_LINK_CAL"));
  }

  /**
   * A refusal that is not about the calendar's row is not swallowed.
   */
  @Test
  void aRefusalWithNoWinningRowIsRethrown() {
    DataIntegrityViolationException refusal = new DataIntegrityViolationException("UK_AGENDA_CALENDAR_LINK_HASH");
    when(dao.findByCalendarId(42)).thenReturn(null);
    when(dao.saveAndFlush(any())).thenThrow(refusal);

    assertSame(refusal, assertThrows(DataIntegrityViolationException.class,
                                     () -> storage.save(42, 8, "b".repeat(64), new Date())));
    verify(dao, never()).save(any());
  }

  /**
   * Deleting reports whether there was a link.
   */
  @Test
  void deletingReportsWhetherThereWasALink() {
    CalendarLinkEntity row = new CalendarLinkEntity();
    when(dao.findByCalendarId(42)).thenReturn(row);

    assertTrue(storage.deleteByCalendarId(42));
    verify(dao).delete(row);
    assertFalse(storage.deleteByCalendarId(43));
  }

}
