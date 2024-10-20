/*
 * Copyright (C) 2024 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.notification.pwa;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.core.space.spi.SpaceService;

public class AgendaUpdatedNotificationPwaPlugin extends AgendaNotificationPwaPlugin {
  public AgendaUpdatedNotificationPwaPlugin(InitParams initParams,
                                            ResourceBundleService resourceBundleService, SpaceService spaceService) {
    super(initParams, resourceBundleService, spaceService);
  }


}
