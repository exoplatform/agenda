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
package org.exoplatform.agenda.rest.model;

import java.io.Serializable;

import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventAttachmentEntity implements Serializable, Cloneable {

  private static final long serialVersionUID = -1529182750476812196L;

  private long              id;

  private String            fileId;

  /**
   * Generated ID used to upload a file when creating or editing an event where
   * a new attachment is added. This ID will be used to retrieve File from
   * UploadService. This field is made transient to avoid retrieve it in REST
   * JSON Responses, see {@link JsonGeneratorImpl#createJsonObject(Object)}
   */
  private transient String  uploadId;

  /**
   * Download URL of file
   */
  private String            url;

  /**
   * File name
   */
  private String            name;

  /**
   * Mime typ of the file
   */
  private String            mimeType;

  /**
   * File size in bytes
   */
  private long              size;

  @Override
  public EventAttachmentEntity clone() {// NOSONAR
    return new EventAttachmentEntity(id, fileId, uploadId, url, name, mimeType, size);
  }
}
