package org.exoplatform.agenda.service;

import java.io.FileInputStream;
import java.util.List;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttachment;
import org.exoplatform.download.DownloadService;
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

  /**
   * Generate a new download identifier that the user will be able to use to
   * download a resource
   * 
   * @param attachmentId technical identifier of {@link EventAttachment}
   * @return generated downloadId coming from {@link DownloadService}
   */
  String generateEventAttachmentDownloadLink(long attachmentId);

  /**
   * Write file in storage, JCR if ecms installed or FileService by default by
   * example
   * 
   * @param eventId {@link Event} on which the file will be attached
   * @param fileName Attached file name
   * @param mimeType Attached file mimeType
   * @param inputStream Attached file content
   * @param creatorIdentityId User willing to attach the file
   * @return stored file identifier
   * @throws Exception when an error happens while storing file
   */
  String writeFileToStorage(long eventId,
                            String fileName,
                            String mimeType,
                            FileInputStream inputStream,
                            long creatorIdentityId) throws Exception; // NOSONAR

}
