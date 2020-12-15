package org.exoplatform.agenda.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.plugin.RemoteProviderDefinitionPlugin;
import org.exoplatform.social.core.identity.model.Identity;

public interface AgendaRemoteEventService {

  /**
   * Register a new Remote provider
   * 
   * @param plugin {@link RemoteProviderDefinitionPlugin}
   * @return {@link CompletableFuture} of created {@link RemoteProvider}
   */
  CompletableFuture<RemoteProvider> addRemoteProvider(RemoteProviderDefinitionPlugin plugin);

  /**
   * @return {@link List} of available events {@link RemoteProvider}
   */
  List<RemoteProvider> getRemoteProviders();

  /**
   * Creates a new {@link RemoteProvider} for remote events
   * 
   * @param remoteProvider remote provider for remote events
   * @return created {@link RemoteProvider}
   */
  RemoteProvider saveRemoteProvider(RemoteProvider remoteProvider);

  /**
   * Save {@link RemoteProvider} status
   * 
   * @param remoteProviderName Remote provider name
   * @param enabled whether enabled (true) or disabled (false)
   * @return saved {@link RemoteProvider}
   */
  RemoteProvider saveRemoteProviderStatus(String remoteProviderName, boolean enabled);

  /**
   * Save {@link RemoteProvider} Client API Key
   * 
   * @param remoteProviderName Remote provider name
   * @param apiKey Client API Key used to allow users access remote connector
   *          API to retrieve and change events on their accounts
   * @return saved {@link RemoteProvider}
   */
  RemoteProvider saveRemoteProviderApiKey(String remoteProviderName, String apiKey);

  /**
   * Creates or updates {@link RemoteEvent} of user
   * 
   * @param eventId technical identifier {@link Event} to attach to remote
   *          provider
   * @param remoteEvent {@link RemoteEvent} with remoteId and
   *          {@link RemoteProvider} provider name information
   * @param userIdentityId User {@link Identity} identitifer who attached event
   *          to remote provider
   * @return created/updated {@link RemoteEvent}
   */
  RemoteEvent saveRemoteEvent(long eventId, RemoteEvent remoteEvent, long userIdentityId);

  /**
   * Creates or updates {@link RemoteEvent} of user
   * 
   * @param remoteEvent {@link RemoteEvent} with remoteId and
   *          {@link RemoteProvider} provider name information
   * @return created/updated {@link RemoteEvent}
   */
  RemoteEvent saveRemoteEvent(RemoteEvent remoteEvent);

  /**
   * Retrieves remote event information for a given user
   * 
   * @param eventId {@link Event} technical identifier
   * @param userIdentityId {@link Identity} identifier
   * @return {@link RemoteEvent} with RemoteId and {@link RemoteProvider}
   *         information
   */
  RemoteEvent findRemoteEvent(long eventId, long userIdentityId);

}
