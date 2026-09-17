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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.exoplatform.agenda.constant.CalendarShareLevel;
import org.exoplatform.agenda.constant.CalendarShareSource;
import org.exoplatform.agenda.dao.CalendarShareDAO;
import org.exoplatform.agenda.entity.CalendarShareEntity;
import org.exoplatform.agenda.model.CalendarShare;

/**
 * Runs the share storage behind a real Spring cache manager (EXO-90357), the
 * only harness that can see a key drift: the reads every ACL decision relies
 * on are served from the cache, the writes evict exactly the keys they touch,
 * and a revoke makes the refusal immediate. The repository is a stand-in over
 * a list, so what the engine would answer after each write is what the cache
 * must answer too.
 */
class CalendarShareStorageCacheTest {

  private static final long                VIEWER   = 7;

  private static final long                OTHER    = 8;

  private static final long                CALENDAR = 42;

  private AnnotationConfigApplicationContext context;

  private CalendarShareStorage             storage;

  private CalendarShareDAO                 dao;

  private List<CalendarShareEntity>        rows;

  /**
   * Starts a context holding the storage, a concurrent-map cache manager and
   * a repository stand-in.
   */
  @BeforeEach
  void startContext() {
    context = new AnnotationConfigApplicationContext(CacheConfiguration.class);
    storage = context.getBean(CalendarShareStorage.class);
    dao = context.getBean(CalendarShareDAO.class);
    rows = context.getBean(Rows.class).rows;
  }

  /**
   * Closes the context.
   */
  @AfterEach
  void stopContext() {
    context.close();
  }

  /**
   * The viewer's list is read once and served from the cache afterwards, on
   * the viewer's own key: another viewer's read is another load.
   */
  @Test
  void theSharedCalendarsOfAViewerAreReadOnce() {
    rows.add(row(CALENDAR, VIEWER));

    assertTrue(storage.isSharedWith(CALENDAR, VIEWER));
    assertTrue(storage.isSharedWith(CALENDAR, VIEWER));
    assertFalse(storage.isSharedWith(CALENDAR, OTHER));
    assertFalse(storage.isSharedWith(CALENDAR, OTHER));

    verify(dao, times(1)).findCalendarLevelsByShareeIdentityId(VIEWER);
    verify(dao, times(1)).findCalendarLevelsByShareeIdentityId(OTHER);
  }

  /**
   * A revoke evicts the sharee's cached list: the refusal is immediate, and
   * the other sharees' lists are not dropped for it.
   */
  @Test
  void aRevokeMakesTheRefusalImmediate() {
    rows.add(row(CALENDAR, VIEWER));
    rows.add(row(CALENDAR, OTHER));
    assertTrue(storage.isSharedWith(CALENDAR, VIEWER));
    assertTrue(storage.isSharedWith(CALENDAR, OTHER));

    assertTrue(storage.delete(CALENDAR, VIEWER));

    assertFalse(storage.isSharedWith(CALENDAR, VIEWER), "the refusal must not wait for the cache to expire");
    assertTrue(storage.isSharedWith(CALENDAR, OTHER));
    verify(dao, times(2)).findCalendarLevelsByShareeIdentityId(VIEWER);
    verify(dao, times(1)).findCalendarLevelsByShareeIdentityId(OTHER);
  }

  /**
   * A new share evicts the sharee's cached list: the admission is immediate.
   */
  @Test
  void aNewShareIsAdmittedImmediately() {
    assertFalse(storage.isSharedWith(CALENDAR, VIEWER));

    storage.save(CALENDAR, VIEWER, 1, CalendarShareLevel.VIEW, CalendarShareSource.EXO, null, null, new Date());

    assertTrue(storage.isSharedWith(CALENDAR, VIEWER));
  }

  /**
   * A calendar's shares are read once and served from the cache, and a
   * delivery or a hide on one of them refreshes the calendar's list.
   */
  @Test
  void aCalendarsSharesAreReadOnceAndRefreshedOnWrite() {
    rows.add(row(CALENDAR, VIEWER));
    assertEquals(1, storage.getShares(CALENDAR).size());
    assertEquals(1, storage.getShares(CALENDAR).size());
    verify(dao, times(1)).findByCalendarIdOrderByCreatedDateAscIdAsc(anyLong(), any());

    CalendarShare delivered = storage.setDelivery(CALENDAR, VIEWER, "caldav:1", "/cal/7/");
    assertNotNull(delivered);
    assertEquals("caldav:1", storage.getShare(CALENDAR, VIEWER).getDeliveredTo());

    assertNotNull(storage.setHidden(CALENDAR, VIEWER, true));
    assertTrue(storage.getShare(CALENDAR, VIEWER).isHidden());
    assertNull(storage.setHidden(CALENDAR, OTHER, true), "no share, nothing to hide");
  }

  /**
   * Deleting a calendar's shares, whose sharees are not read first, drops
   * every viewer's cached list.
   */
  @Test
  void deletingACalendarsSharesDropsEveryViewersList() {
    rows.add(row(CALENDAR, VIEWER));
    rows.add(row(CALENDAR, OTHER));
    assertTrue(storage.isSharedWith(CALENDAR, VIEWER));
    assertTrue(storage.isSharedWith(CALENDAR, OTHER));

    storage.deleteByCalendarId(CALENDAR);

    assertFalse(storage.isSharedWith(CALENDAR, VIEWER));
    assertFalse(storage.isSharedWith(CALENDAR, OTHER));
  }

  /**
   * The viewer's level map is what the cache holds, and the level is a field
   * of the value, never folded into the key (EXO-90378): two viewers of the
   * same calendar at two levels each read their own.
   */
  @Test
  void eachViewerIsServedTheirOwnLevel() {
    rows.add(row(CALENDAR, VIEWER, CalendarShareLevel.EDIT));
    rows.add(row(CALENDAR, OTHER, CalendarShareLevel.VIEW));

    assertEquals(CalendarShareLevel.EDIT, storage.getShareLevel(CALENDAR, VIEWER));
    assertEquals(CalendarShareLevel.VIEW, storage.getShareLevel(CALENDAR, OTHER));
    assertEquals(CalendarShareLevel.EDIT, storage.getShareLevel(CALENDAR, VIEWER));
    assertNull(storage.getShareLevel(CALENDAR, 99), "an identity with no share has no level");

    verify(dao, times(1)).findCalendarLevelsByShareeIdentityId(VIEWER);
    verify(dao, times(1)).findCalendarLevelsByShareeIdentityId(OTHER);
  }

  /**
   * A downgrade is refused on the very next read (EXO-90378): setLevel evicts
   * the sharee's map, and only theirs.
   */
  @Test
  void aDowngradeIsRefusedOnTheNextRead() {
    rows.add(row(CALENDAR, VIEWER, CalendarShareLevel.EDIT));
    rows.add(row(CALENDAR, OTHER, CalendarShareLevel.EDIT));
    assertEquals(CalendarShareLevel.EDIT, storage.getShareLevel(CALENDAR, VIEWER));
    assertEquals(CalendarShareLevel.EDIT, storage.getShareLevel(CALENDAR, OTHER));

    assertNotNull(storage.setLevel(CALENDAR, VIEWER, CalendarShareLevel.VIEW));

    assertEquals(CalendarShareLevel.VIEW,
                 storage.getShareLevel(CALENDAR, VIEWER),
                 "the narrower level must not wait for the cache to expire");
    assertEquals(CalendarShareLevel.EDIT, storage.getShareLevel(CALENDAR, OTHER), "and only the levelled sharee is dropped");
    assertEquals(CalendarShareLevel.VIEW, storage.getShare(CALENDAR, VIEWER).getLevel(), "the calendar's list is refreshed too");
    verify(dao, times(2)).findCalendarLevelsByShareeIdentityId(VIEWER);
    verify(dao, times(1)).findCalendarLevelsByShareeIdentityId(OTHER);
  }

  /**
   * An upgrade is admitted on the very next read, and levelling a colleague
   * who has no share answers nothing.
   */
  @Test
  void anUpgradeIsAdmittedOnTheNextRead() {
    rows.add(row(CALENDAR, VIEWER, CalendarShareLevel.VIEW));
    assertEquals(CalendarShareLevel.VIEW, storage.getShareLevel(CALENDAR, VIEWER));

    assertNotNull(storage.setLevel(CALENDAR, VIEWER, CalendarShareLevel.EDIT));

    assertEquals(CalendarShareLevel.EDIT, storage.getShareLevel(CALENDAR, VIEWER));
    assertNull(storage.setLevel(CALENDAR, OTHER, CalendarShareLevel.EDIT), "no share, nothing to level");
  }

  /**
   * A row of the stand-in.
   *
   * @param calendarId calendar identifier
   * @param shareeId sharee identity identifier
   * @return the row
   */
  private static CalendarShareEntity row(long calendarId, long shareeId) {
    return row(calendarId, shareeId, CalendarShareLevel.VIEW);
  }

  /**
   * A row of the stand-in, at a given level.
   *
   * @param calendarId calendar identifier
   * @param shareeId sharee identity identifier
   * @param level what the sharee may do with the calendar
   * @return the row
   */
  private static CalendarShareEntity row(long calendarId, long shareeId, CalendarShareLevel level) {
    CalendarShareEntity entity = new CalendarShareEntity();
    entity.setLevel(level);
    entity.setId((long) (calendarId * 1000 + shareeId));
    entity.setCalendarId(calendarId);
    entity.setShareeIdentityId(shareeId);
    entity.setGrantedById(1);
    entity.setSource(CalendarShareSource.EXO);
    entity.setCreatedDate(new Date());
    return entity;
  }

  /**
   * The rows the repository stand-in answers from.
   */
  static class Rows {
    final List<CalendarShareEntity> rows = new ArrayList<>();
  }

  /**
   * The storage over a concurrent-map cache manager and a repository whose
   * answers come from a list the tests write.
   */
  @Configuration
  @EnableCaching
  static class CacheConfiguration {

    /**
     * @return the rows
     */
    @Bean
    Rows rows() {
      return new Rows();
    }

    /**
     * @return a real cache manager
     */
    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(CalendarShareStorage.BY_VIEWER_CACHE, CalendarShareStorage.BY_CALENDAR_CACHE);
    }

    /**
     * A repository answering from the rows.
     *
     * @param rows the rows
     * @return the stand-in
     */
    @Bean
    CalendarShareDAO calendarShareDAO(Rows rows) {
      CalendarShareDAO dao = mock(CalendarShareDAO.class);
      when(dao.findCalendarLevelsByShareeIdentityId(anyLong())).thenAnswer(invocation -> {
        long shareeId = invocation.getArgument(0);
        return rows.rows.stream()
                        .filter(row -> row.getShareeIdentityId() == shareeId)
                        .map(row -> new Object[] { row.getCalendarId(), row.getLevel() })
                        .toList();
      });
      when(dao.findByCalendarIdOrderByCreatedDateAscIdAsc(anyLong(), any())).thenAnswer(invocation -> {
        long calendarId = invocation.getArgument(0);
        return rows.rows.stream().filter(row -> row.getCalendarId() == calendarId).toList();
      });
      when(dao.findByCalendarIdAndShareeIdentityId(anyLong(), anyLong())).thenAnswer(invocation -> {
        long calendarId = invocation.getArgument(0);
        long shareeId = invocation.getArgument(1);
        return rows.rows.stream()
                        .filter(row -> row.getCalendarId() == calendarId && row.getShareeIdentityId() == shareeId)
                        .findFirst()
                        .orElse(null);
      });
      when(dao.saveAndFlush(any())).thenAnswer(invocation -> {
        CalendarShareEntity entity = invocation.getArgument(0);
        entity.setId(entity.getCalendarId() * 1000 + entity.getShareeIdentityId());
        rows.rows.add(entity);
        return entity;
      });
      when(dao.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      org.mockito.Mockito.doAnswer(invocation -> {
        rows.rows.remove(invocation.<CalendarShareEntity> getArgument(0));
        return null;
      }).when(dao).delete(any());
      when(dao.deleteByCalendarId(anyLong())).thenAnswer(invocation -> {
        long calendarId = invocation.getArgument(0);
        int before = rows.rows.size();
        rows.rows.removeIf(row -> row.getCalendarId() == calendarId);
        return before - rows.rows.size();
      });
      return dao;
    }

    /**
     * @param dao the repository
     * @return the storage under test
     */
    @Bean
    CalendarShareStorage calendarShareStorage(CalendarShareDAO dao) {
      return new CalendarShareStorage(dao);
    }
  }

}
