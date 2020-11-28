/*
 * Copyright (C) 2020 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
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
