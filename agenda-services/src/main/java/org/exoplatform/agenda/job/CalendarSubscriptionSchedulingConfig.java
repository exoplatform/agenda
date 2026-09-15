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

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's scheduler in agenda's context, for the calendar subscription
 * refresh (EXO-90278) — agenda's other jobs are Quartz jobs declared to the
 * kernel. Declared beside the job it serves, as caldav-integration's
 * {@code SchedulingConfig} is, so that whoever reads the package sees what it
 * switches on.
 */
@Configuration
@EnableScheduling
public class CalendarSubscriptionSchedulingConfig {
}
