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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.exoplatform.agenda.dao.CalendarLinkChangelogTestAccess;
import org.exoplatform.agenda.dao.CalendarLinkDAO;
import org.exoplatform.agenda.entity.CalendarLinkEntity;
import org.exoplatform.agenda.model.CalendarLink;

/**
 * Runs the storage's lost-race retry through real Spring Data repositories: a
 * plain {@link EntityManagerFactory} bean — the part of the platform's shape
 * that is known, portal's {@code PersistenceUnitIntegration} exposes the
 * kernel's — and a {@link JpaTransactionManager}, so that each repository call
 * runs in its own transaction. <b>Which transaction manager the platform wires
 * for an addon is not verified here</b>: none was found declared in portal,
 * social or commons-exo, so this pins the retry under Spring Data's standard
 * transactional repositories, not under the production bean. The schema is
 * agenda's real changelog on HSQLDB.
 * <p>
 * The race is staged, not timed: the repository the storage talks to answers
 * "no link" on its first lookup although another manager's row is already
 * committed, which is exactly what the second of two simultaneous creations
 * sees. Its insert is then refused by the engine, and the retry must turn it
 * into an update of the winning row in a fresh persistence context.
 */
class CalendarLinkStorageRaceTest {

  private static String                      url;

  private Connection                         keeper;

  private AnnotationConfigApplicationContext context;

  /**
   * Builds the schema and the Spring context.
   *
   * @throws Exception when the database cannot be built
   */
  @BeforeEach
  void startContext() throws Exception {
    url = "jdbc:hsqldb:mem:agenda-link-race-" + System.nanoTime();
    keeper = DriverManager.getConnection(url, "sa", "");
    CalendarLinkChangelogTestAccess.update(keeper);
    context = new AnnotationConfigApplicationContext(JpaConfiguration.class);
  }

  /**
   * Closes the context and drops the database.
   *
   * @throws Exception when it cannot be shut down
   */
  @AfterEach
  void stopContext() throws Exception {
    context.close();
    try (Statement statement = keeper.createStatement()) {
      statement.execute("SHUTDOWN");
    }
    keeper.close();
  }

  /**
   * The second of two simultaneous creations replaces the first link instead of
   * failing, through the real repository and transaction manager.
   */
  @Test
  void theLoserOfACreationRaceReplacesTheWinningLink() {
    CalendarLinkDAO repository = context.getBean(CalendarLinkDAO.class);
    CalendarLinkStorage winner = new CalendarLinkStorage(repository);
    winner.save(42, 7, "a".repeat(64), "enc-a", new Date(1000));

    CalendarLinkStorage loser = new CalendarLinkStorage(blindOnFirstLookup(repository));
    CalendarLink link = loser.save(42, 8, "b".repeat(64), "enc-b", new Date(2000));

    assertEquals(8, link.getCreatorId());
    assertEquals(1, repository.count(), "still one link for the calendar");
    CalendarLinkEntity stored = repository.findByCalendarId(42);
    assertEquals(8, stored.getCreatorId(), "the later creation won, as a reset would");
    assertEquals("b".repeat(64), stored.getTokenHash());
    assertEquals("enc-b", stored.getTokenEncrypted());
  }

  /**
   * A repository that misses the row on its first lookup, as a concurrent
   * creation does, and delegates everything else.
   *
   * @param repository the real repository
   * @return the staged repository
   */
  private CalendarLinkDAO blindOnFirstLookup(CalendarLinkDAO repository) {
    AtomicBoolean firstLookup = new AtomicBoolean(true);
    return (CalendarLinkDAO) Proxy.newProxyInstance(getClass().getClassLoader(),
                                                    new Class<?>[] { CalendarLinkDAO.class },
                                                    (proxy, method, arguments) -> {
                                                      if ("findByCalendarId".equals(method.getName())
                                                          && firstLookup.getAndSet(false)) {
                                                        return null;
                                                      }
                                                      try {
                                                        return method.invoke(repository, arguments);
                                                      } catch (InvocationTargetException e) {
                                                        throw e.getCause();
                                                      }
                                                    });
  }

  /**
   * Spring Data over the test persistence unit, as the platform wires an addon.
   */
  @Configuration
  @EnableJpaRepositories(basePackageClasses = CalendarLinkDAO.class,
                         includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CalendarLinkDAO.class))
  static class JpaConfiguration {

    /**
     * The entity manager factory, a plain bean as portal exposes it.
     *
     * @return the factory
     */
    @Bean
    EntityManagerFactory entityManagerFactory() {
      return Persistence.createEntityManagerFactory("agenda-calendar-link-test", Map.of("jakarta.persistence.jdbc.url", url));
    }

    /**
     * The transaction manager repository calls run in.
     *
     * @param entityManagerFactory the factory
     * @return the transaction manager
     */
    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
      return new JpaTransactionManager(entityManagerFactory);
    }
  }

}
