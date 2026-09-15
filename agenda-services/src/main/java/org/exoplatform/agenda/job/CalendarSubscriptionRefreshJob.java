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
package org.exoplatform.agenda.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.meeds.common.ContainerTransactional;

import org.exoplatform.agenda.service.AgendaCalendarSubscriptionService;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.monitor.jvm.ServerStartupWaiter;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Refreshes the calendar subscriptions whose refresh is due (EXO-90278).
 * <p>
 * <b>Every node fires it.</b> Spring's scheduler is node-local on this platform —
 * there is no clustered {@code TaskScheduler} — so the job holds no logic and no
 * lock of its own: the service claims each subscription in the database before
 * reading its feed, and a node that loses the claim moves on.
 * <p>
 * <b>Not before the server is up.</b> A run finding the portal container not
 * started, or the HTTP connector not started yet, does nothing: the kernel's
 * {@link ServerStartupWaiter} is asked, without waiting, and the next run tries
 * again.
 * <p>
 * The cron is {@code exo.agenda.calendarSubscription.refresh.cron}, every ten
 * minutes by default; {@code -} turns the job off, and subscriptions are then
 * refreshed only when their owner asks.
 */
@Component
public class CalendarSubscriptionRefreshJob {

  /** What the job is, for the startup waiter's logs. */
  static final String                             SUBJECT = "Agenda calendar subscription refresh";

  private static final Log                        LOG     = ExoLogger.getLogger(CalendarSubscriptionRefreshJob.class);

  private final AgendaCalendarSubscriptionService subscriptionService;

  private final int                               batchSize;

  /**
   * Builds the job.
   *
   * @param subscriptionService the service holding every refresh rule
   * @param batchSize most subscriptions refreshed by one run of one node
   */
  @Autowired
  public CalendarSubscriptionRefreshJob(AgendaCalendarSubscriptionService subscriptionService,
                                        @Value("${exo.agenda.calendarSubscription.refresh.batchSize:20}")
                                        int batchSize) {
    this.subscriptionService = subscriptionService;
    this.batchSize = batchSize;
  }

  /**
   * Refreshes a batch of due subscriptions, once the server is up.
   * <p>
   * {@code @ContainerTransactional} establishes the portal container on the
   * scheduler's thread, which has none bound.
   */
  @Scheduled(cron = "${exo.agenda.calendarSubscription.refresh.cron:0 */10 * * * ?}")
  @ContainerTransactional
  public void refresh() {
    if (!isServerStarted()) {
      LOG.debug("The server is not started yet; calendar subscriptions are refreshed by a later run");
      return;
    }
    long start = System.currentTimeMillis();
    int refreshed = subscriptionService.refreshDueSubscriptions(batchSize);
    if (refreshed > 0) {
      LOG.info("Refreshed {} calendar subscription(s) in {} ms", refreshed, System.currentTimeMillis() - start);
    }
  }

  /**
   * Whether the portal container is started and no HTTP connector is still
   * starting.
   *
   * @return true when a refresh may run
   */
  protected boolean isServerStarted() {
    ExoContainer container = PortalContainer.getInstance();
    return container != null && container.isStarted() && !ServerStartupWaiter.isHttpConnectorPending(container, SUBJECT);
  }

}
