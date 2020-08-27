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
package org.exoplatform.agenda.util;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Date;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

public class AgendaDateUtils {
  public static final DateTimeFormatter RFC_3339_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]XXX")
                                                                              .withResolverStyle(ResolverStyle.LENIENT);

  private AgendaDateUtils() {
  }

  public static ZonedDateTime parseRFC3339ToZonedDateTime(String dateString) {
    if (StringUtils.isBlank(dateString)) {
      return null;
    }
    return ZonedDateTime.parse(dateString, RFC_3339_FORMATTER);
  }

  public static String toRFC3339Date(ZonedDateTime zonedDateTime) {
    if (zonedDateTime == null) {
      return null;
    }
    return zonedDateTime.format(RFC_3339_FORMATTER);
  }

  public static String toRFC3339Date(ZonedDateTime zonedDateTime, ZoneOffset zoneOffset) {
    if (zonedDateTime == null) {
      return null;
    }
    return zonedDateTime.withZoneSameInstant(zoneOffset).format(RFC_3339_FORMATTER);
  }

  public static Date parseRFC3339Date(String dateString) {
    if (StringUtils.isBlank(dateString)) {
      return null;
    }
    ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateString, RFC_3339_FORMATTER);
    return Date.from(zonedDateTime.toInstant());
  }

  public static String toRFC3339Date(Date dateTime) {
    if (dateTime == null) {
      return null;
    }
    ZonedDateTime zonedDateTime = ZonedDateTime.from(dateTime.toInstant().atOffset(ZoneOffset.UTC));
    return zonedDateTime.format(RFC_3339_FORMATTER);
  }

  public static TimeZone getUserTimezone(Identity userIdentity) {
    if (userIdentity == null || userIdentity.getProfile() == null || userIdentity.getProfile().getTimeZone() == null) {
      return TimeZone.getDefault();
    } else {
      String timeZoneId = userIdentity.getProfile().getTimeZone();
      return TimeZone.getTimeZone(timeZoneId);
    }
  }

  public static Date toDate(ZonedDateTime datetime) {
    if (datetime == null) {
      return null;
    }
    return new Date(datetime.toEpochSecond() * 1000);
  }

  public static ZonedDateTime fromDate(Date date) {
    if (date == null) {
      return null;
    }
    return date.toInstant().atZone(ZoneOffset.UTC);
  }

  public static TimeZone getUserTimezone(String username) {
    IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
    Identity userIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username);
    if (userIdentity == null) {
      return TimeZone.getTimeZone(ZoneOffset.UTC);
    }
    return getUserTimezone(userIdentity);
  }
}
