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
package org.exoplatform.agenda.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import jakarta.persistence.EntityManager;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;

/**
 * Records the statements Hibernate sends from the current thread, between
 * {@link #start()} and {@link #stop()}, each with whether a JPA transaction
 * was active when it was sent. Set as the session factories' statement
 * inspector by the surefire configuration of this module, it records nothing
 * on a thread that did not start it.
 */
public class RecordingStatementInspector implements StatementInspector {

  private static final long                               serialVersionUID = 2817370143870962613L;

  private static final Pattern                            LOCKING_EVENT_READ =
                                                                             Pattern.compile("from exo_agenda_event\\s.*for update",
                                                                                             Pattern.DOTALL);

  private static final ThreadLocal<List<RecordedStatement>> RECORDED           = new ThreadLocal<>();

  /**
   * Starts recording the statements of the current thread.
   */
  public static void start() {
    RECORDED.set(new ArrayList<>());
  }

  /**
   * Stops recording the statements of the current thread.
   *
   * @return the statements recorded since {@link #start()}, empty when it was
   *         not called
   */
  public static List<RecordedStatement> stop() {
    List<RecordedStatement> recorded = RECORDED.get();
    RECORDED.remove();
    return recorded == null ? List.of() : recorded;
  }

  @Override
  public String inspect(String sql) {
    List<RecordedStatement> recorded = RECORDED.get();
    if (recorded != null) {
      recorded.add(new RecordedStatement(sql, isTransactionActive()));
    }
    return sql;
  }

  private static boolean isTransactionActive() {
    ExoContainer container = ExoContainerContext.getCurrentContainerIfPresent();
    EntityManagerService entityManagerService = container == null ? null
                                                                  : container.getComponentInstanceOfType(EntityManagerService.class);
    EntityManager entityManager = entityManagerService == null ? null : entityManagerService.getEntityManager();
    return entityManager != null && entityManager.getTransaction().isActive();
  }

  /**
   * A statement sent, and whether a JPA transaction was active then.
   *
   * @param sql the statement
   * @param inTransaction whether a JPA transaction was active
   */
  public record RecordedStatement(String sql, boolean inTransaction) {

    /**
     * @return true when the statement reads an event row with a write lock
     */
    public boolean isLockingReadOfAnEvent() {
      return LOCKING_EVENT_READ.matcher(sql.toLowerCase(Locale.ROOT)).find();
    }

    @Override
    public String toString() {
      return (inTransaction ? "[tx] " : "[--] ") + sql;
    }
  }
}
