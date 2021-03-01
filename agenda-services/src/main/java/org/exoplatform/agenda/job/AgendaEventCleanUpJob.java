package org.exoplatform.agenda.job;

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * A job to clean up canceled events every 3 days
 */
public class AgendaEventCleanUpJob implements Job {

  private static final Log   LOG = ExoLogger.getLogger(AgendaEventCleanUpJob.class);

  private ExoContainer       container;

  private AgendaEventService agendaEventService;

  public AgendaEventCleanUpJob() {
    this.container = PortalContainer.getInstance();
  }

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    ExoContainer currentContainer = ExoContainerContext.getCurrentContainer();
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(this.container);
    try {
      getAgendaEventService().deleteEventByStatus(EventStatus.CANCELLED);
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
  
}
