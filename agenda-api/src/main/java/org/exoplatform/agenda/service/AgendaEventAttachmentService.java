package org.exoplatform.agenda.service;

import java.util.List;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttachment;
import org.exoplatform.social.core.identity.model.Identity;

public interface AgendaEventAttachmentService {

  /**
   * Save {@link Event} attachments
   * 
   * @param eventId technichal identifier of {@link Event}
   * @param attachments {@link List} of {@link EventAttachment}
   * @param creatorIdentityId {@link Identity} technical identifier of user
   */
  void saveEventAttachments(long eventId, List<EventAttachment> attachments, long creatorIdentityId);

  /**
   * Return the list of attachments of an event
   * 
   * @param eventId agenda {@link Event} identifier
   * @return {@link List} of {@link EventAttachment}
   */
  List<EventAttachment> getEventAttachments(long eventId);

  /**
   * Retrieve event attachement identified by its technical identifier
   * 
   * @param attachmentId technical identifier of {@link EventAttachment}
   * @return {@link EventAttachment} if found else null
   */
  EventAttachment getEventAttachmentById(long attachmentId);

}
