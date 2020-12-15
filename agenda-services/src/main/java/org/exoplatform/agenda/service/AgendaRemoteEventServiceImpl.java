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
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class AgendaRemoteEventServiceImpl implements AgendaRemoteEventService {

  private static final Log         LOG = ExoLogger.getLogger(AgendaRemoteEventServiceImpl.class);

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
          remoteProvider = new RemoteProvider(0,
                                              plugin.getConnectorName(),
                                              plugin.getConnectorAPIKey(),
                                              plugin.isEnabled());
          remoteProvider = saveRemoteProvider(remoteProvider);
        } else if (StringUtils.isBlank(remoteProvider.getApiKey())) {
          if (StringUtils.isBlank(plugin.getConnectorAPIKey())) {
            LOG.warn("Agenda connector {} has an empty API key, thus the connector will be disabled", plugin.getConnectorName());
            remoteProvider.setEnabled(false);
          } else {
            remoteProvider.setApiKey(plugin.getConnectorAPIKey());
          }
          remoteProvider = saveRemoteProvider(remoteProvider);
        }
        return remoteProvider;
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
  public RemoteProvider saveRemoteProviderStatus(String remoteProviderName, boolean enabled) {
    if (StringUtils.isBlank(remoteProviderName)) {
      throw new IllegalStateException("remoteProviderName is mandatory");
    }
    RemoteProvider remoteProvider = remoteEventStorage.getRemoteProviderByName(remoteProviderName);
    if (remoteProvider == null) {
      throw new IllegalStateException("Remote provider not found with name " + remoteProviderName);
    }
    if (enabled && StringUtils.isBlank(remoteProvider.getApiKey())) {
      throw new IllegalStateException("Can't enable connector " + remoteProviderName + " since it doesn't have an API Key");
    }
    remoteProvider.setEnabled(enabled);
    return remoteEventStorage.saveRemoteProvider(remoteProvider);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RemoteProvider saveRemoteProviderApiKey(String remoteProviderName, String apiKey) {
    if (StringUtils.isBlank(remoteProviderName)) {
      throw new IllegalStateException("remoteProviderName is mandatory");
    }
    RemoteProvider remoteProvider = remoteEventStorage.getRemoteProviderByName(remoteProviderName);
    if (remoteProvider == null) {
      throw new IllegalStateException("Remote provider not found with name " + remoteProviderName);
    }
    if (StringUtils.isBlank(apiKey)) {
      remoteProvider.setEnabled(false);
    }
    remoteProvider.setApiKey(apiKey);
    return remoteEventStorage.saveRemoteProvider(remoteProvider);
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
