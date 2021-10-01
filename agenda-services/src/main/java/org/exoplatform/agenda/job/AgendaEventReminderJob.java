// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.job;

import org.quartz.*;

import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.service.AgendaEventReminderService;
import org.exoplatform.container.*;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * A job to send reminders of current minutes switch computed
 * {@link EventReminder#getDatetime()}
 */
@DisallowConcurrentExecution
public class AgendaEventReminderJob implements Job {

  private static final Log           LOG = ExoLogger.getLogger(AgendaEventReminderJob.class);

  private ExoContainer               container;

  private AgendaEventReminderService agendaEventReminderService;

  public AgendaEventReminderJob() {
    this.container = PortalContainer.getInstance();
  }

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    ExoContainer currentContainer = ExoContainerContext.getCurrentContainer();
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(this.container);
    try {
      getAgendaEventReminderService().sendReminders();
    } catch (Exception e) {
      LOG.error("Error while computing reminder date of events", e);
    } finally {
      RequestLifeCycle.end();
      ExoContainerContext.setCurrentContainer(currentContainer);
    }
  }

  public AgendaEventReminderService getAgendaEventReminderService() {
    if (agendaEventReminderService == null) {
      agendaEventReminderService = this.container.getComponentInstanceOfType(AgendaEventReminderService.class);
    }
    return agendaEventReminderService;
  }

}
