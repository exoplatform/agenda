/*
 * Copyright (C) 2021 eXo Platform SAS.
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
package org.exoplatform.agenda.model;

import java.util.HashMap;
import java.util.Map;

import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
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
