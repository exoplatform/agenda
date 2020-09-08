package org.exoplatform.agenda.service;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.StringUtils;

import org.exoplatform.agenda.model.EventAttachment;
import org.exoplatform.agenda.model.EventAttachmentUpload;
import org.exoplatform.agenda.storage.AgendaEventAttachmentStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.download.DownloadResource;
import org.exoplatform.download.DownloadService;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.upload.UploadResource;
import org.exoplatform.upload.UploadService;

public class AgendaEventAttachmentServiceImpl implements AgendaEventAttachmentService {

  public static final String           AGENDA_FILE_SERVICE_NS = "agenda";

  private static final Log             LOG                    = ExoLogger.getLogger(AgendaEventAttachmentServiceImpl.class);

  private ListenerService              listenerService;

  private DownloadService              downloadService;

  private UploadService                uploadService;

  private FileService                  fileService;

  private AgendaEventAttachmentStorage attachmentStorage;

  public AgendaEventAttachmentServiceImpl(AgendaEventAttachmentStorage eventAttachmentStorage,
                                          ListenerService listenerService,
                                          FileService fileService,
                                          DownloadService downloadService,
                                          UploadService uploadService) {
    this.listenerService = listenerService;
    this.attachmentStorage = eventAttachmentStorage;
    this.fileService = fileService;
    this.downloadService = downloadService;
    this.uploadService = uploadService;
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
      if (eventAttachment instanceof EventAttachmentUpload) {
        String uploadId = ((EventAttachmentUpload) eventAttachment).getUploadId();
        UploadResource uploadResource = uploadService.getUploadResource(uploadId);
        if (uploadResource == null) {
          LOG.warn("Can't find an uploaded attachement to event with id : {}", uploadId);
        } else {
          String fileName = uploadResource.getFileName();
          String mimeType = uploadResource.getMimeType();
          String storeLocation = uploadResource.getStoreLocation();
          File file = new File(storeLocation);
          if (file.exists()) {
            try (FileInputStream inputStream = new FileInputStream(file)) {
              String fileId = writeFileToStorage(eventId, fileName, mimeType, inputStream, creatorIdentityId);
              attachmentStorage.createEventAttachment(eventId, fileId);
            } catch (Exception e) {
              LOG.warn("An error happened while writing file '{}' read from location '{}'", fileName, storeLocation, e);
            }
          } else {
            LOG.warn("Can't find file '{}' read from location '{}'", fileName, storeLocation);
          }
        }
      } else if (eventAttachment.getFileId() != null) {
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

  @Override
  public String generateEventAttachmentDownloadLink(long attachmentId) {
    EventAttachment attachment = attachmentStorage.getEventAttachmentById(attachmentId);
    if (attachment == null) {
      return null;
    }
    try {
      long fileId = parseFileId(attachment);
      boolean isFileSupported = fileId <= 0;
      if (isFileSupported) {
        return null;
      }
      FileItem file = fileService.getFile(fileId);
      InputStreamDownloadResource resource = new InputStreamDownloadResource(file);
      String downloadResourceId = downloadService.addDownloadResource(resource);
      return downloadService.getDownloadLink(downloadResourceId);
    } catch (IOException | FileStorageException e) {
      throw new IllegalStateException("Error while retrieving file of attachment with id " + attachmentId);
    }
  }

  @Override
  public String writeFileToStorage(long eventId,
                                   String fileName,
                                   String mimeType,
                                   FileInputStream inputStream,
                                   long creatorIdentityId) throws Exception {
    FileItem fileItem = new FileItem(null,
                                     fileName,
                                     mimeType,
                                     AGENDA_FILE_SERVICE_NS,
                                     inputStream.available(),
                                     new Date(),
                                     String.valueOf(creatorIdentityId),
                                     false,
                                     inputStream);
    fileItem = fileService.writeFile(fileItem);
    Long fileId = fileItem.getFileInfo().getId();
    return String.valueOf(fileId);
  }

  private long parseFileId(EventAttachment attachment) {
    long fileId = 0;
    try {
      fileId = Long.parseLong(attachment.getFileId());
    } catch (NumberFormatException e) {
      LOG.debug("File id {} is not supported by current implementation", attachment.getFileId());
    }
    return fileId;
  }

  public class InputStreamDownloadResource extends DownloadResource {
    private InputStream inputStream;

    public InputStreamDownloadResource(FileItem fileItem) throws IOException {
      super(fileItem.getFileInfo().getMimetype());
      setDownloadName(fileItem.getFileInfo().getName());
      this.inputStream = fileItem.getAsStream();
    }

    public InputStream getInputStream() {
      return inputStream;
    }
  }

}
