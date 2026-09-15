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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import org.exoplatform.agenda.service.AgendaCalendarSubscriptionService;

/**
 * Pins the scheduled refresh of calendar subscriptions (EXO-90278): glue only,
 * silent until the server is started, its cron a property that can turn it off,
 * and the scheduler switched on in agenda's context.
 */
class CalendarSubscriptionRefreshJobTest {

  /**
   * A job whose view of the server is given.
   *
   * @param service the service
   * @param started whether the server is started
   * @return the job
   */
  private static CalendarSubscriptionRefreshJob job(AgendaCalendarSubscriptionService service, boolean started) {
    return new CalendarSubscriptionRefreshJob(service, 20) {
      @Override
      protected boolean isServerStarted() {
        return started;
      }
    };
  }

  /**
   * Before the server is started, a run reads nothing.
   */
  @Test
  void aRunBeforeTheServerIsStartedDoesNothing() {
    AgendaCalendarSubscriptionService service = mock(AgendaCalendarSubscriptionService.class);

    job(service, false).refresh();

    verifyNoInteractions(service);
  }

  /**
   * Once started, a run hands a batch to the service, which claims each
   * subscription.
   */
  @Test
  void aRunHandsABatchToTheService() {
    AgendaCalendarSubscriptionService service = mock(AgendaCalendarSubscriptionService.class);

    job(service, true).refresh();

    verify(service).refreshDueSubscriptions(20);
  }

  /**
   * The cron is a deployment property, ten minutes by default, and the
   * scheduler is enabled.
   *
   * @throws Exception never
   */
  @Test
  void theCronIsAPropertyAndTheSchedulerIsEnabled() throws Exception {
    Scheduled scheduled = CalendarSubscriptionRefreshJob.class.getMethod("refresh").getAnnotation(Scheduled.class);
    assertNotNull(scheduled);
    assertTrue(scheduled.cron().startsWith("${exo.agenda.calendarSubscription.refresh.cron:0 */10 * * * ?"), scheduled.cron());
    assertNotNull(CalendarSubscriptionSchedulingConfig.class.getAnnotation(EnableScheduling.class));
  }

}
