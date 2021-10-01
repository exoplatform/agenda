// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.constant;

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

public enum ReminderPeriodType {
  MINUTE(ChronoUnit.MINUTES),
  HOUR(ChronoUnit.HOURS),
  DAY(ChronoUnit.DAYS),
  WEEK(ChronoUnit.WEEKS);

  private TemporalUnit temporalUnit;

  private ReminderPeriodType(TemporalUnit temporalUnit) {
    this.temporalUnit = temporalUnit;
  }

  public TemporalUnit getTemporalUnit() {
    return temporalUnit;
  }
}
