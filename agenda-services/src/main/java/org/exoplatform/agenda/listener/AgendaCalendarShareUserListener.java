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
package org.exoplatform.agenda.listener;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.service.AgendaCalendarShareService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserEventListener;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * Deletes the calendar shares a deleted user held, as the colleague they were
 * shared with and as the owner of the calendars they shared (EXO-90357). Glue
 * only, registered on the organization service from {@code configuration.xml}
 * as the other user listeners of the platform are.
 * <p>
 * Read before the user goes: the identity is still resolvable by username at
 * {@code preDelete}, and is what the share rows name.
 */
public class AgendaCalendarShareUserListener extends UserEventListener {

  private static final Log LOG = ExoLogger.getLogger(AgendaCalendarShareUserListener.class);

  /**
   * Deletes the shares of a user about to be deleted.
   *
   * @param user the user
   */
  @Override
  public void preDelete(User user) {
    if (user == null || StringUtils.isBlank(user.getUserName())) {
      return;
    }
    try {
      Identity identity = CommonsUtils.getService(IdentityManager.class).getOrCreateUserIdentity(user.getUserName());
      if (identity != null) {
        CommonsUtils.getService(AgendaCalendarShareService.class).deleteSharesOfUser(Long.parseLong(identity.getId()));
      }
    } catch (RuntimeException e) {
      // The deletion of the user must not be stopped by a cleanup: a share row
      // left behind grants nothing to a user who no longer exists
      LOG.warn("The calendar shares of user {} could not be deleted with the user", user.getUserName(), e);
    }
  }

}
