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
package org.exoplatform.agenda.plugin;

import javax.mail.internet.InternetAddress;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.social.core.identity.IdentityProvider;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;

public class AgendaExternalUserIdentityProvider extends IdentityProvider<InternetAddress> {

  public static final String NAME = "AGENDA_EXTERNAL_USER";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public InternetAddress findByRemoteId(String remoteId) {
    return getInternetAddress(remoteId);
  }

  @Override
  public Identity createIdentity(InternetAddress emailAdress) {
    if (emailAdress == null || StringUtils.isBlank(emailAdress.getAddress())) {
      return null;
    }
    String email = emailAdress.getAddress().toLowerCase();
    return new Identity(NAME, email);
  }

  @Override
  public void populateProfile(Profile profile, InternetAddress emailAdress) {
    profile.setProperty(Profile.EMAIL, emailAdress.getAddress().toLowerCase());
    if (StringUtils.isNotBlank(emailAdress.getPersonal())) {
      profile.setProperty(Profile.FULL_NAME, emailAdress.getPersonal());
    }
  }

  private static InternetAddress getInternetAddress(String remoteId) {
    InternetAddress[] addresses = null;
    try {
      addresses = InternetAddress.parse(remoteId);
    } catch (Exception e) {
      return null;
    }
    if (addresses == null || addresses.length != 1) {
      return null;
    }
    return addresses[0];
  }
}
