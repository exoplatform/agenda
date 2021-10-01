// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

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
