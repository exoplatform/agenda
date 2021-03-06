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

import java.time.*;
import java.util.List;

import org.quartz.*;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.AgendaEventReminderService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.container.*;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * This Job will be executed to compute exact reminder date for events of next
 * two days. This will ensure to treat all occurrences of recurrent events.
 * Knowing that Occurrences aren't stored in database, the reminder date of
 * occurrences couldn't be retrieved by a simple Query. In addition, Reminder
 * Job (that will be executed each minute), has to be performant to be able to
 * send reminders on time. The outcome of this job is to store the exact
 * reminder date of upcoming events (including occurrences) the next two days.
 */
@DisallowConcurrentExecution
public class DailyReminderComputingJob implements Job {

  private static final Log           LOG = ExoLogger.getLogger(DailyReminderComputingJob.class);

  private ExoContainer               container;

  private AgendaEventService         agendaEventService;

  private AgendaEventReminderService agendaEventReminderService;

  public DailyReminderComputingJob() {
    this.container = PortalContainer.getInstance();
  }

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    ExoContainer currentContainer = ExoContainerContext.getCurrentContainer();
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(this.container);
    try {
      ZonedDateTime start = ZonedDateTime.now();
      ZonedDateTime end = start.plusDays(getAgendaEventReminderService().getReminderComputingPeriod());

      List<Event> events = getAgendaEventService().getParentRecurrentEvents(start, end, ZoneOffset.UTC);
      for (Event recurrentEvent : events) {
        List<Event> occurrences = getAgendaEventService().getEventOccurrencesInPeriod(recurrentEvent,
                                                                                      start,
                                                                                      end,
                                                                                      ZoneOffset.UTC,
                                                                                      0);
        for (Event occurrence : occurrences) {
          getAgendaEventService().saveEventExceptionalOccurrence(recurrentEvent.getId(), occurrence.getOccurrence().getId());
        }
      }
    } catch (Exception e) {
      LOG.error("Error while computing reminder date of events", e);
    } finally {
      RequestLifeCycle.end();
      ExoContainerContext.setCurrentContainer(currentContainer);
    }
  }

  private AgendaEventService getAgendaEventService() {
    if (agendaEventService == null) {
      agendaEventService = this.container.getComponentInstanceOfType(AgendaEventService.class);
    }
    return agendaEventService;
  }

  public AgendaEventReminderService getAgendaEventReminderService() {
    if (agendaEventReminderService == null) {
      agendaEventReminderService = this.container.getComponentInstanceOfType(AgendaEventReminderService.class);
    }
    return agendaEventReminderService;
  }
}
