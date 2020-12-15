package org.exoplatform.agenda.storage;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.dao.RemoteEventDAO;
import org.exoplatform.agenda.dao.RemoteProviderDAO;
import org.exoplatform.agenda.entity.RemoteEventEntity;
import org.exoplatform.agenda.entity.RemoteProviderEntity;
import org.exoplatform.agenda.model.RemoteEvent;
import org.exoplatform.agenda.model.RemoteProvider;
import org.exoplatform.agenda.util.EntityMapper;

public class AgendaRemoteEventStorage {

  private RemoteProviderDAO remoteProviderDAO;

  private RemoteEventDAO    remoteEventDAO;

  public AgendaRemoteEventStorage(RemoteProviderDAO remoteProviderDAO, RemoteEventDAO remoteEventDAO) {
    this.remoteProviderDAO = remoteProviderDAO;
    this.remoteEventDAO = remoteEventDAO;
  }

  public List<RemoteProvider> getRemoteProviders() {
    List<RemoteProviderEntity> remoteProviders = remoteProviderDAO.findAll();
    return remoteProviders == null ? Collections.emptyList()
                                   : remoteProviders.stream()
                                                    .map(remoteProviderEntity -> EntityMapper.fromEntity(remoteProviderEntity))
                                                    .collect(Collectors.toList());
  }

  public RemoteProvider getRemoteProviderById(long remoteProviderId) {
    RemoteProviderEntity remoteProviderEntity = remoteProviderDAO.find(remoteProviderId);
    if (remoteProviderEntity == null) {
      return null;
    }
    return EntityMapper.fromEntity(remoteProviderEntity);
  }

  public RemoteProvider saveRemoteProvider(RemoteProvider remoteProvider) {
    RemoteProviderEntity remoteProviderEntity = EntityMapper.toEntity(remoteProvider);
    if (remoteProviderEntity.getId() == null) {
      RemoteProviderEntity existingRemoteProviderEntity = remoteProviderDAO.findByName(remoteProvider.getName());
      if (existingRemoteProviderEntity == null) {
        remoteProviderEntity = remoteProviderDAO.create(remoteProviderEntity);
      } else {
        remoteProviderEntity.setId(existingRemoteProviderEntity.getId());
        remoteProviderEntity = remoteProviderDAO.update(remoteProviderEntity);
      }
    } else {
      remoteProviderEntity = remoteProviderDAO.update(remoteProviderEntity);
    }
    return EntityMapper.fromEntity(remoteProviderEntity);
  }

  public RemoteProvider getRemoteProviderByName(String connectorName) {
    RemoteProviderEntity remoteProviderEntity = remoteProviderDAO.findByName(connectorName);
    if (remoteProviderEntity == null) {
      return null;
    }
    return EntityMapper.fromEntity(remoteProviderEntity);
  }

  public RemoteEventEntity deleteRemoteEvent(long eventId, long identityId) {
    RemoteEventEntity remoteEventEntity = remoteEventDAO.findRemoteEvent(eventId, identityId);
    if (remoteEventEntity != null) {
      remoteEventDAO.delete(remoteEventEntity);
    }
    return remoteEventEntity;
  }

  public RemoteEvent saveRemoteEvent(RemoteEvent remoteEvent) {
    long identityId = remoteEvent.getIdentityId();
    long eventId = remoteEvent.getEventId();

    RemoteEventEntity existingRemoteEventEntity = remoteEventDAO.findRemoteEvent(eventId, identityId);
    if (existingRemoteEventEntity == null) {
      if (StringUtils.isNotBlank(remoteEvent.getRemoteId())
          && (remoteEvent.getRemoteProviderId() > 0 || StringUtils.isNotBlank(remoteEvent.getRemoteProviderName()))) {
        RemoteProviderEntity remoteProviderEntity = getRemoteProviderFromEvent(remoteEvent);
        remoteEvent.setRemoteProviderId(remoteProviderEntity.getId());
        remoteEvent.setRemoteProviderName(remoteProviderEntity.getName());
        RemoteEventEntity remoteEventEntity = EntityMapper.toEntity(remoteEvent);
        remoteEventEntity = remoteEventDAO.create(remoteEventEntity);
        return EntityMapper.fromEntity(remoteEventEntity, remoteProviderEntity);
      }
      return null;
    } else if (StringUtils.isBlank(remoteEvent.getRemoteId())
        || (remoteEvent.getRemoteProviderId() == 0 && StringUtils.isBlank(remoteEvent.getRemoteProviderName()))) {
      remoteEventDAO.delete(existingRemoteEventEntity);
      return null;
    } else {
      RemoteProviderEntity remoteProviderEntity = getRemoteProviderFromEvent(remoteEvent);
      remoteEvent.setId(existingRemoteEventEntity.getId());
      remoteEvent.setRemoteProviderId(remoteProviderEntity.getId());
      RemoteEventEntity remoteEventEntity = EntityMapper.toEntity(remoteEvent);
      remoteEventEntity = remoteEventDAO.update(remoteEventEntity);
      return EntityMapper.fromEntity(remoteEventEntity, remoteProviderEntity);
    }
  }

  public RemoteEvent findRemoteEvent(long eventId, long userIdentityId) {
    RemoteEventEntity remoteEventEntity = remoteEventDAO.findRemoteEvent(eventId, userIdentityId);
    if (remoteEventEntity == null) {
      return null;
    }
    RemoteProviderEntity remoteProviderEntity = remoteProviderDAO.find(remoteEventEntity.getRemoteProviderId());
    return EntityMapper.fromEntity(remoteEventEntity, remoteProviderEntity);
  }

  public RemoteProviderEntity getRemoteProviderFromEvent(RemoteEvent remoteEvent) {
    RemoteProviderEntity remoteProviderEntity = null;
    if (remoteEvent.getRemoteProviderId() > 0) {
      remoteProviderEntity = remoteProviderDAO.find(remoteEvent.getRemoteProviderId());
    } else if (StringUtils.isNotBlank(remoteEvent.getRemoteProviderName())) {
      remoteProviderEntity = remoteProviderDAO.findByName(remoteEvent.getRemoteProviderName());
    }
    return remoteProviderEntity;
  }

}
