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
package org.exoplatform.agenda.service;

import java.util.Collections;
import java.util.List;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Reads the calendars an owner's subscriptions fill (EXO-90373), from a Kernel
 * component that needs the Spring subscription service, and never fails the
 * read it serves: a listing that cannot ask keeps the events it already had.
 * <p>
 * The same shape as {@link CalendarShareAccess}, and for the same reason: the
 * event service is a Kernel component and the subscription service a Spring
 * bean, resolved on first use and replaceable in tests.
 */
public class CalendarSubscriptionAccess {

  private static final Log                        LOG = ExoLogger.getLogger(CalendarSubscriptionAccess.class);

  private AgendaCalendarSubscriptionService       subscriptionService;

  /**
   * Builds a lookup that resolves the bean on first use.
   */
  public CalendarSubscriptionAccess() {
    // The bean is resolved lazily
  }

  /**
   * Builds a lookup over a known service, for tests and for the Spring side.
   *
   * @param subscriptionService the subscription service, may be null
   */
  public CalendarSubscriptionAccess(AgendaCalendarSubscriptionService subscriptionService) {
    this.subscriptionService = subscriptionService;
  }

  /**
   * The calendars the subscriptions of these owners fill.
   *
   * @param ownerIds identity identifiers of the owners, may be empty
   * @return technical identifiers of their subscription calendars, empty when
   *         there is none or the service cannot be asked
   */
  public List<Long> getSubscriptionCalendarIds(List<Long> ownerIds) {
    AgendaCalendarSubscriptionService service = service();
    if (service == null || ownerIds == null || ownerIds.isEmpty()) {
      return Collections.emptyList();
    }
    try {
      return service.getSubscriptionCalendarIds(ownerIds);
    } catch (RuntimeException | LinkageError e) {
      LOG.warn("The subscribed calendars of owners {} could not be read; the listing is served without them", ownerIds, e);
      return Collections.emptyList();
    }
  }

  /**
   * The subscription service, resolved on first use.
   *
   * @return the service, or null when the addon's Spring context has none
   */
  private AgendaCalendarSubscriptionService service() {
    if (subscriptionService == null) {
      try {
        subscriptionService = ExoContainerContext.getService(AgendaCalendarSubscriptionService.class);
      } catch (RuntimeException | LinkageError e) {
        LOG.debug("The calendar subscription service cannot be reached yet", e);
      }
    }
    return subscriptionService;
  }

}
