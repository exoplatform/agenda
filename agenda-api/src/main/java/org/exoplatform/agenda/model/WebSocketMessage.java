// Copyright (C) 2021 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.model;

import java.util.HashMap;
import java.util.Map;

import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;

import groovy.transform.ToString;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@ToString
public class WebSocketMessage {

  String              wsEventName;

  Map<String, Object> params;

  public WebSocketMessage(String wsEventName) {
    this.wsEventName = wsEventName;
  }

  public WebSocketMessage(String wsEventId, Object... data) {
    this.wsEventName = wsEventId;
    if (data != null && data.length > 0) {
      params = new HashMap<>();
      for (Object object : data) {
        if (object != null) {
          params.put(object.getClass().getSimpleName().toLowerCase(), object);
        }
      }
    }
  }

  /**
   * Add a new parameter to WebSocket message to transmit to end user
   * 
   * @param key params {@link Map} key
   * @param value params {@link Map} key
   * @return the previous value of the key in existing {@link Map}
   */
  public Object addParam(String key, Object value) {
    if (params == null) {
      params = new HashMap<>();
    }
    return params.put(key, value);
  }

  @Override
  public String toString() {
    try {
      return new JsonGeneratorImpl().createJsonObject(this).toString();
    } catch (JsonException e) {
      throw new IllegalStateException("Error parsing current global object to string", e);
    }
  }

}
