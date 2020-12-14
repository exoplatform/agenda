package org.exoplatform.agenda.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.entity.RemoteEventEntity;
import org.exoplatform.agenda.model.RemoteEvent;
import org.exoplatform.agenda.model.RemoteProvider;
import org.exoplatform.agenda.plugin.RemoteProviderDefinitionPlugin;
import org.exoplatform.agenda.storage.AgendaRemoteEventStorage;
import org.exoplatform.agenda.util.EntityMapper;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;

public class AgendaRemoteEventServiceImpl implements AgendaRemoteEventService {

  private AgendaRemoteEventStorage remoteEventStorage;

  public AgendaRemoteEventServiceImpl(AgendaRemoteEventStorage agendaRemoteEventStorage) {
    this.remoteEventStorage = agendaRemoteEventStorage;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletableFuture<RemoteProvider> addRemoteProvider(RemoteProviderDefinitionPlugin plugin) {
    if (plugin == null) {
      throw new IllegalArgumentException("plugin is mandatory");
    }
    PortalContainer container = PortalContainer.getInstance();

    return CompletableFuture.supplyAsync(() -> {
      ExoContainerContext.setCurrentContainer(container);
      RequestLifeCycle.begin(container);
      try {
        RemoteProvider remoteProvider = remoteEventStorage.getRemoteProviderByName(plugin.getConnectorName());
        if (remoteProvider == null) {
          remoteProvider = new RemoteProvider(0, plugin.getConnectorName(), plugin.isEnabled());
        }
        return saveRemoteProvider(remoteProvider);
      } finally {
        RequestLifeCycle.end();
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<RemoteProvider> getRemoteProviders() {
    return remoteEventStorage.getRemoteProviders();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RemoteProvider saveRemoteProvider(RemoteProvider remoteProvider) {
    return remoteEventStorage.saveRemoteProvider(remoteProvider);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void saveRemoteProviderStatus(String remoteProviderName, boolean enabled) {
    if (StringUtils.isBlank(remoteProviderName)) {
      throw new IllegalStateException("remoteProviderName is mandatory");
    }
    remoteEventStorage.saveRemoteProviderStatus(remoteProviderName, enabled);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RemoteEvent saveRemoteEvent(RemoteEvent remoteEvent) {
    return remoteEventStorage.saveRemoteEvent(remoteEvent);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RemoteEvent saveRemoteEvent(long eventId, RemoteEvent remoteEvent, long userIdentityId) {
    if (eventId <= 0) {
      throw new IllegalArgumentException("eventId is mandatory");
    }
    if (userIdentityId <= 0) {
      throw new IllegalArgumentException("userIdentityId is mandatory");
    }
    if (remoteEvent == null || StringUtils.isBlank(remoteEvent.getRemoteId())
        || (remoteEvent.getRemoteProviderId() <= 0 && StringUtils.isBlank(remoteEvent.getRemoteProviderName()))) {
      RemoteEventEntity deletedRemoteEvent = remoteEventStorage.deleteRemoteEvent(eventId, userIdentityId);
      return deletedRemoteEvent == null ? null : EntityMapper.fromEntity(deletedRemoteEvent, null);
    } else {
      remoteEvent.setIdentityId(userIdentityId);
      remoteEvent.setEventId(eventId);
      return saveRemoteEvent(remoteEvent);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RemoteEvent findRemoteEvent(long eventId, long userIdentityId) {
    return remoteEventStorage.findRemoteEvent(eventId, userIdentityId);
  }
}
