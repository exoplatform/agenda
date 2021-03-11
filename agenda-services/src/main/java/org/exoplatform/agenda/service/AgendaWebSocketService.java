/*
 * Copyright (C) 2021 eXo Platform SAS.
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
package org.exoplatform.agenda.service;

import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.mortbay.cometd.continuation.EXoContinuationBayeux;

import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.model.WebSocketMessage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.ws.frameworks.cometd.ContinuationService;

/**
 * A service to push notifications from server to browsers of users about
 * modifications made by other users on events.
 */
public class AgendaWebSocketService {

  private static final Log      LOG            = ExoLogger.getLogger(AgendaWebSocketService.class);

  public static final String    COMETD_CHANNEL = "/eXo/Application/Addons/Agenda";

  private IdentityManager       identityManager;

  private SpaceService          spaceService;

  private AgendaCalendarService agendaCalendarService;

  private ContinuationService   continuationService;

  private String                cometdContextName;

  public AgendaWebSocketService(SpaceService spaceService,
                                IdentityManager identityManager,
                                AgendaCalendarService agendaCalendarService,
                                ContinuationService continuationService,
                                EXoContinuationBayeux continuationBayeux) {
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.agendaCalendarService = agendaCalendarService;
    this.continuationService = continuationService;
    this.cometdContextName = continuationBayeux.getCometdContextName();
  }

  /**
   * Propagate an event from Backend to frontend to add dynamism in pages
   * 
   * @param wsEventName event name that will allow Browser to distinguish which
   *          behavior to adopt in order to update UI
   * @param eventModification The event modification object that provides the
   *          list of modifications made on event
   */
  public void sendMessage(String wsEventName, AgendaEventModification eventModification) {
    long calendarId = eventModification.getCalendarId();
    org.exoplatform.agenda.model.Calendar calendar = agendaCalendarService.getCalendarById(calendarId);
    if (calendar == null) {
      LOG.warn("Calendar with id " + calendarId + " wasn't found");
      return;
    }
    long ownerId = calendar.getOwnerId();
    Identity identity = identityManager.getIdentity(String.valueOf(ownerId));
    if (identity == null) {
      LOG.warn("Identity with id " + ownerId + " wasn't found");
      return;
    }
    Set<String> recipientUsers = new HashSet<>();
    String remoteId = identity.getRemoteId();
    if (StringUtils.equals(OrganizationIdentityProvider.NAME, identity.getProviderId())) {
      recipientUsers.add(remoteId);
    } else {
      Space space = spaceService.getSpaceByPrettyName(remoteId);
      if (space == null) {
        LOG.warn("Space with pretty name " + remoteId + " wasn't found");
        return;
      }
      Collections.addAll(recipientUsers, space.getMembers());
    }
    sendMessage(wsEventName, recipientUsers, eventModification);
  }

  /**
   * Propagate an event from Backend to frontend to add dynamism in pages
   * 
   * @param wsEventName event name that will allow Browser to distinguish which
   *          behavior to adopt in order to update UI
   * @param recipientUsers {@link Collection} of usernames of receivers
   * @param params an Array of parameters to include in message sent via
   *          WebSocket
   */
  public void sendMessage(String wsEventName, Collection<String> recipientUsers, Object... params) {
    WebSocketMessage messageObject = new WebSocketMessage(wsEventName, params);
    sendMessage(messageObject, recipientUsers);
  }

  /**
   * Propagate an event from Backend to frontend to add dynamism in pages
   * 
   * @param messageObject {@link WebSocketMessage} to transmit via WebSocket
   * @param recipientUsers {@link Collection} of usernames of receivers
   */
  public void sendMessage(WebSocketMessage messageObject, Collection<String> recipientUsers) {
    String message = messageObject.toString();
    for (String recipientUser : recipientUsers) {
      if (continuationService.isPresent(recipientUser)) {
        continuationService.sendMessage(recipientUser, COMETD_CHANNEL, message);
      }
    }
  }

  /**
   * @return 'cometd' webapp context name
   */
  public String getCometdContextName() {
    return cometdContextName;
  }

  /**
   * Generate a cometd Token for each user
   * 
   * @param username user name for whom the token will be generated
   * @return generated cometd token
   */
  public String getUserToken(String username) {
    try {
      return continuationService.getUserToken(username);
    } catch (Exception e) {
      LOG.warn("Could not retrieve continuation token for user " + username, e);
      return "";
    }
  }

}
