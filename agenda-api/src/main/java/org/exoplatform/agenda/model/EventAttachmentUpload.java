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
package org.exoplatform.agenda.model;

import lombok.*;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class EventAttachmentUpload extends EventAttachment {

  /**
   * Generated ID used to upload a file when creating or editing an event where
   * a new attachment is added. This ID will be used to retrieve File from
   * UploadService.
   */
  private String uploadId;

  public EventAttachmentUpload(long id,
                               long fileId,
                               long eventId,
                               String uploadId) {
    super(id, fileId, eventId);
    this.uploadId = uploadId;
  }

  @Override
  protected EventAttachmentUpload clone() { // NOSONAR
    return new EventAttachmentUpload(getId(), getFileId(), getEventId(), getUploadId());
  }
}
