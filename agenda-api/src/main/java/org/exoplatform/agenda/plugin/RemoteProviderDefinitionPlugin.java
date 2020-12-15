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
package org.exoplatform.agenda.plugin;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.container.component.BaseComponentPlugin;
import org.exoplatform.container.xml.InitParams;

import lombok.Getter;
import lombok.Setter;

public class RemoteProviderDefinitionPlugin extends BaseComponentPlugin {

  @Getter
  @Setter
  private String  connectorName;

  @Getter
  @Setter
  private String  connectorAPIKey;

  @Getter
  @Setter
  private boolean enabled;

  public RemoteProviderDefinitionPlugin(InitParams params) {
    if (params == null || !params.containsKey("connectorName")) {
      throw new IllegalStateException("Init parameter 'connectorName' is mandatory");
    } else {
      this.connectorName = params.getValueParam("connectorName").getValue();
      if (StringUtils.isBlank(this.connectorName)) {
        throw new IllegalStateException("Init parameter 'connectorName' can't be empty");
      }
    }

    this.enabled = !params.containsKey("connectorEnabled")
        || Boolean.parseBoolean(params.getValueParam("connectorEnabled").getValue());

    this.connectorAPIKey = params.containsKey("connectorAPIKey") ? params.getValueParam("connectorAPIKey").getValue() : null;
  }
}
