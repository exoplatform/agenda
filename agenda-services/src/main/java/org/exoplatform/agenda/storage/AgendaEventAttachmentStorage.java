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
package org.exoplatform.agenda.storage;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.exoplatform.agenda.dao.EventAttachmentDAO;
import org.exoplatform.agenda.dao.EventDAO;
import org.exoplatform.agenda.entity.EventAttachmentEntity;
import org.exoplatform.agenda.entity.EventEntity;
import org.exoplatform.agenda.model.EventAttachment;
import org.exoplatform.agenda.util.EntityMapper;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.ListenerService;

public class AgendaEventAttachmentStorage {

  private EventDAO           eventDAO;

  private EventAttachmentDAO eventAttachmentDAO;

  private ListenerService    listenerService;

  public AgendaEventAttachmentStorage(EventDAO eventDAO,
                                      EventAttachmentDAO eventAttachmentDAO,
                                      ListenerService listenerService) {
    this.eventDAO = eventDAO;
    this.eventAttachmentDAO = eventAttachmentDAO;
    this.listenerService = listenerService;
  }

  public void createEventAttachment(long eventId, String fileId) {
    EventAttachmentEntity eventAttachmentEntity = new EventAttachmentEntity();
    EventEntity eventEntity = eventDAO.find(eventId);
    if (eventEntity == null) {
      throw new IllegalStateException("Can't find event with id : " + eventId);
    }
    eventAttachmentEntity.setEvent(eventEntity);
    eventAttachmentEntity.setFileId(fileId);
    eventAttachmentDAO.create(eventAttachmentEntity);
    Utils.broadcastEvent(listenerService, "exo.agenda.event.attachment.created", eventId, fileId);
  }

  public void removeEventAttachment(long attachmentId) {
    EventAttachmentEntity eventAttachmentEntity = eventAttachmentDAO.find(attachmentId);
    if (eventAttachmentEntity != null) {
      eventAttachmentDAO.delete(eventAttachmentEntity);
      Utils.broadcastEvent(listenerService,
                           "exo.agenda.event.attachment.deleted",
                           eventAttachmentEntity.getEvent().getId(),
                           eventAttachmentEntity.getFileId());
    }
  }

  public List<EventAttachment> getEventAttachments(long eventId) {
    List<EventAttachmentEntity> eventAttachments = eventAttachmentDAO.getEventAttachments(eventId);
    if (eventAttachments == null) {
      return Collections.emptyList();
    }
    return eventAttachments.stream().map(EntityMapper::fromEntity).collect(Collectors.toList());
  }

  public EventAttachment getEventAttachmentById(long attachmentId) {
    EventAttachmentEntity eventAttachmentEntity = eventAttachmentDAO.find(attachmentId);
    if (eventAttachmentEntity == null) {
      return null;
    }
    return EntityMapper.fromEntity(eventAttachmentEntity);
  }

}
