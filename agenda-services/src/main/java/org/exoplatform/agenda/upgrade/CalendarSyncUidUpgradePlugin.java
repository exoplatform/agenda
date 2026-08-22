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

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Entity;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Gives an anchor to the calendars that predate the column holding it.
 *
 * <p>
 * EXO-89497 added {@code SYNC_UID} together with the guard in
 * {@code CalendarDAO.create} that mints one for every new calendar. It did not
 * migrate what was already stored, and nothing since can: the service copies
 * the stored value back over whatever a caller passes, so a null stays null,
 * and the DAO mints only on create. A calendar older than that commit would
 * therefore never carry an anchor.
 *
 * <p>
 * That matters because the anchor is what an integration binds to — the CalDAV
 * connector deliberately binds by it rather than by id, since an id does not
 * survive a restore that renumbers. A calendar with no anchor is excluded from
 * synchronisation entirely, and the calendar this hits hardest is the user's
 * default one, which holds most of their events.
 *
 * <p>
 * A fresh database needs none of this. Every deployment upgrading across
 * EXO-89497 does, because every user has a default calendar created long
 * before it.
 */
public class CalendarSyncUidUpgradePlugin extends UpgradeProductPlugin {

  private static final Log           LOG        = ExoLogger.getExoLogger(CalendarSyncUidUpgradePlugin.class);

  /**
   * How many calendars are read at a time. Bounded rather than one sweep: this
   * table has a row per user and per space calendar, and a migration is not a
   * reason to hold the whole of it in memory.
   */
  private static final int           PAGE_SIZE  = 500;

  /**
   * The entity as JPQL names it — {@code @Entity(name = "AgendaCalendar")},
   * which is not the class name. Read from the annotation rather than written
   * out, so a rename cannot leave these queries pointing at something that no
   * longer exists: a mocked entity manager resolves no query, so nothing but a
   * running database would otherwise notice.
   */
  static final String                ENTITY     = CalendarEntity.class.getAnnotation(Entity.class).name();

  /**
   * The calendars still missing an anchor, oldest first so a run that is
   * interrupted resumes where it stopped rather than starting over.
   */
  private static final String        SELECT_SQL = """
      SELECT c.id FROM %s c WHERE c.syncUid IS NULL ORDER BY c.id
      """.formatted(ENTITY);

  /**
   * Writes one anchor.
   * <p>
   * The {@code IS NULL} in the where clause is not redundant with the select:
   * it makes the write refuse to touch a calendar that has acquired an anchor
   * since the page was read. Overwriting an existing anchor would break every
   * binding already made against it — a worse outcome than the gap this plugin
   * exists to close.
   */
  private static final String        UPDATE_SQL = """
      UPDATE %s c SET c.syncUid = :syncUid WHERE c.id = :id AND c.syncUid IS NULL
      """.formatted(ENTITY);

  private final EntityManagerService entityManagerService;

  /**
   * @param entityManagerService the shared entity manager
   * @param initParams the plugin's declared parameters
   */
  public CalendarSyncUidUpgradePlugin(EntityManagerService entityManagerService, InitParams initParams) {
    super(initParams);
    this.entityManagerService = entityManagerService;
  }

  /**
   * Whether this upgrade has anything to do.
   *
   * <p>
   * A first installation is skipped: its calendars are all created through the
   * DAO that already mints anchors, so there is nothing older to repair.
   *
   * @param newVersion the version being upgraded to
   * @param previousVersion the version being upgraded from, blank on a first
   *          installation
   * @return true when this is an upgrade rather than an installation
   */
  @Override
  public boolean shouldProceedToUpgrade(String newVersion, String previousVersion) {
    return StringUtils.isNotBlank(previousVersion);
  }

  /**
   * Mints an anchor for every calendar lacking one.
   *
   * @param oldVersion the version being upgraded from
   * @param newVersion the version being upgraded to
   */
  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startTime = System.currentTimeMillis();
    LOG.info("Start:: giving a sync uid to the calendars created before EXO-89497 introduced it");
    int migrated = 0;
    List<Long> page = nextPage();
    while (!page.isEmpty()) {
      for (Long id : page) {
        migrated += mintAnchor(id);
      }
      List<Long> next = nextPage();
      if (next.size() == page.size() && next.equals(page)) {
        // The same page twice means nothing was written and the loop would run
        // for ever. Stopping loudly beats spinning: the rows are still there
        // to migrate on the next boot, and the log says a boot was spent on
        // them.
        LOG.warn("End:: {} calendars still carry no sync uid and could not be given one; stopping rather than looping",
                 next.size());
        return;
      }
      page = next;
    }
    LOG.info("End:: {} calendars were given a sync uid in {} ms", migrated, System.currentTimeMillis() - startTime);
  }

  /**
   * One page of calendars still missing an anchor.
   *
   * @return their identifiers, empty when none are left
   */
  @ExoTransactional
  public List<Long> nextPage() {
    return entityManagerService.getEntityManager()
                               .createQuery(SELECT_SQL, Long.class)
                               .setMaxResults(PAGE_SIZE)
                               .getResultList();
  }

  /**
   * Writes an anchor onto one calendar.
   *
   * @param id the calendar to repair
   * @return 1 when it was given one, 0 when it already had one
   */
  @ExoTransactional
  public int mintAnchor(long id) {
    return entityManagerService.getEntityManager()
                               .createQuery(UPDATE_SQL)
                               .setParameter("syncUid", UUID.randomUUID().toString())
                               .setParameter("id", id)
                               .executeUpdate();
  }
}
