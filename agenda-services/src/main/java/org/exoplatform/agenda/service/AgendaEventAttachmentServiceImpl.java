package org.exoplatform.agenda.service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.StringUtils;

import org.exoplatform.agenda.model.EventAttachment;
import org.exoplatform.agenda.storage.AgendaEventAttachmentStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class AgendaEventAttachmentServiceImpl implements AgendaEventAttachmentService {

  public static final String           AGENDA_FILE_SERVICE_NS = "agenda";

  private static final Log             LOG                    = ExoLogger.getLogger(AgendaEventAttachmentServiceImpl.class);

  private ListenerService              listenerService;

  private AgendaEventAttachmentStorage attachmentStorage;

  public AgendaEventAttachmentServiceImpl(AgendaEventAttachmentStorage eventAttachmentStorage,
                                          ListenerService listenerService) {
    this.listenerService = listenerService;
    this.attachmentStorage = eventAttachmentStorage;
  }

  @Override
  public void saveEventAttachments(long eventId, List<EventAttachment> attachments, long creatorIdentityId) {
    List<EventAttachment> savedAttachments = getEventAttachments(eventId);
    List<EventAttachment> newAttachments = attachments == null ? Collections.emptyList() : attachments;
    List<EventAttachment> attachmentsToDelete =
                                              savedAttachments.stream()
                                                              .filter(attachment -> newAttachments.stream()
                                                                                                  .noneMatch(newAttachment -> StringUtils.equals(newAttachment.getFileId(),
                                                                                                                                                 attachment.getFileId())))
                                                              .collect(Collectors.toList());

    // Delete attachments
    for (EventAttachment eventAttachment : attachmentsToDelete) {
      attachmentStorage.removeEventAttachment(eventAttachment.getId());
    }

    List<EventAttachment> attachmentsToCreate =
                                              newAttachments == null ? Collections.emptyList()
                                                                     : newAttachments.stream()
                                                                                     .filter(newAttachment -> savedAttachments.stream()
                                                                                                                              .noneMatch(attachment -> StringUtils.equals(newAttachment.getFileId(),
                                                                                                                                                                          attachment.getFileId())))
                                                                                     .collect(Collectors.toList());
    // Create new attachments
    for (EventAttachment eventAttachment : attachmentsToCreate) {
      if (eventAttachment.getFileId() != null) {
        attachmentStorage.createEventAttachment(eventId, eventAttachment.getFileId());
      } else {
        LOG.warn("Uploaded file attachment doesn't have a fileId neither an uploadId, it will be ignored. Attachment = {} ",
                 eventAttachment);
      }
    }

    Utils.broadcastEvent(listenerService, "exo.agenda.event.attachments.saved", eventId, null);
  }

  @Override
  public List<EventAttachment> getEventAttachments(long eventId) {
    return this.attachmentStorage.getEventAttachments(eventId);
  }

  @Override
  public EventAttachment getEventAttachmentById(long attachmentId) {
    return this.attachmentStorage.getEventAttachmentById(attachmentId);
  }

}
