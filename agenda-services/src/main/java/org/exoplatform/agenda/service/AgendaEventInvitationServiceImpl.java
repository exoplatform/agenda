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
package org.exoplatform.agenda.service;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.plugin.AgendaExternalUserIdentityProvider;
import org.exoplatform.agenda.service.AgendaEventInvitationService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.web.security.codec.CodecInitializer;
import org.exoplatform.web.security.security.TokenServiceInitializationException;

public class AgendaEventInvitationServiceImpl implements AgendaEventInvitationService {

  private static final String SEPARATOR = "|";

  private IdentityManager     identityManager;

  private CodecInitializer    codecInitializer;

  public AgendaEventInvitationServiceImpl(IdentityManager identityManager, CodecInitializer codecInitializer) {
    this.codecInitializer = codecInitializer;
    this.identityManager = identityManager;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String generateEncryptedToken(long eventId, String email) throws TokenServiceInitializationException {
    return generateEncryptedToken(eventId, email, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String generateEncryptedToken(long eventId,
                                       String emailOrUsername,
                                       EventAttendeeResponse response) throws TokenServiceInitializationException {
    if (eventId <= 0) {
      throw new IllegalArgumentException("eventId is mandatory");
    }
    if (StringUtils.isBlank(emailOrUsername)) {
      throw new IllegalArgumentException("email is mandatory");
    }
    StringBuilder tokenFlatStringBuilder = new StringBuilder().append(String.valueOf(eventId))
                                                              .append(SEPARATOR)
                                                              .append(emailOrUsername);
    if (response != null) {
      tokenFlatStringBuilder.append(SEPARATOR).append(response.getValue());
    }
    String tokenFlat = tokenFlatStringBuilder.toString();
    return codecInitializer.getCodec().encode(tokenFlat);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Identity readUserIdentity(String token,
                                   long eventId,
                                   EventAttendeeResponse response) throws TokenServiceInitializationException,
                                                                   IllegalAccessException {
    String tokenFlat = codecInitializer.getCodec().decode(token);
    String[] tokenParts = tokenFlat.split(SEPARATOR);
    if (tokenParts.length < 2) {
      throw new IllegalAccessException("Wrong token format");
    }
    String eventIdString = tokenParts[0];
    if (!eventIdString.equals(String.valueOf(eventId))) {
      throw new IllegalAccessException("Wrong eventId from token");
    }
    if (response != null) {
      String responseString = tokenParts.length > 2 ? tokenParts[2] : null;
      if (!StringUtils.equals(responseString, response.getValue())) {
        throw new IllegalAccessException("Wrong response from token");
      }
    }
    String emailOrUsername = tokenParts[1];
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, emailOrUsername);
    if (identity == null) {
      identity = identityManager.getOrCreateIdentity(AgendaExternalUserIdentityProvider.NAME, emailOrUsername);
    }
    return identity;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EventAttendeeResponse getEventResponse(String identityId, long eventId) throws ObjectNotFoundException,
                                                                                 IllegalAccessException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void sendEventResponse(String identityId, long eventId, EventAttendeeResponse response) throws ObjectNotFoundException,
                                                                                                 IllegalAccessException {
    // TODO Auto-generated method stub
  }
}
