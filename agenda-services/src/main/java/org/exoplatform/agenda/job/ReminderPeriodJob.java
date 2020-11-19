package org.exoplatform.agenda.job;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.scheduler.PeriodJob;
import org.quartz.JobDataMap;

public class ReminderPeriodJob extends PeriodJob {
    private JobDataMap jobDataMap;
  public ReminderPeriodJob(InitParams params) throws Exception {
    super(params);
    jobDataMap = new JobDataMap();
  }

  public JobDataMap getJobDataMap() {
    return jobDataMap;
  }
}
