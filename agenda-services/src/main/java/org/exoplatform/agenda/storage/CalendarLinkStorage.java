/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.storage;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import org.exoplatform.agenda.dao.CalendarLinkDAO;
import org.exoplatform.agenda.entity.CalendarLinkEntity;
import org.exoplatform.agenda.model.CalendarLink;

import jakarta.persistence.PersistenceException;

/**
 * Maps calendar links between their rows and {@link CalendarLink}. No cache:
 * a feed is fetched every few hours per subscriber, and a cached digest would
 * keep a reset link answering until the cache let it go.
 */
@Component
public class CalendarLinkStorage {

  private final CalendarLinkDAO calendarLinkDAO;

  /**
   * Builds the storage.
   *
   * @param calendarLinkDAO the repository of calendar link rows
   */
  @Autowired
  public CalendarLinkStorage(CalendarLinkDAO calendarLinkDAO) {
    this.calendarLinkDAO = calendarLinkDAO;
  }

  /**
   * Reads the link of a calendar.
   *
   * @param calendarId technical identifier of the calendar
   * @return the link, or null when the calendar has none
   */
  public CalendarLink getByCalendarId(long calendarId) {
    return toModel(calendarLinkDAO.findByCalendarId(calendarId));
  }

  /**
   * Reads the link a token digest opens.
   *
   * @param tokenHash lowercase hexadecimal SHA-256 digest of a token
   * @return the link, or null when no link carries that digest
   */
  public CalendarLink getByTokenHash(String tokenHash) {
    return toModel(calendarLinkDAO.findByTokenHash(tokenHash));
  }

  /**
   * Writes the link of a calendar, creating the row or replacing the one the
   * calendar already has.
   * <p>
   * Two managers creating a link for the same calendar at the same moment both
   * find no row, and the unique constraint on the calendar refuses the second
   * insert. That refusal is not an error for the second manager: their link
   * simply replaces the first one, as a reset would, so the insert is retried
   * once as an update of the row that won.
   * <p>
   * The insert is flushed, so that the refusal surfaces here on every engine —
   * with a sequence generator the row would otherwise only reach the database
   * at commit, past this method. It is caught both as Spring's translated
   * exception and as the raw JPA one, because whether a repository call is
   * translated depends on the context it runs in; a refusal that is not about
   * the calendar's row is rethrown, since no row is found on the retry.
   *
   * @param calendarId technical identifier of the calendar
   * @param creatorId identity identifier of the user creating the link
   * @param tokenHash digest of the new token
   * @param tokenEncrypted the new token encrypted by the platform codec
   * @param createdDate when the link is created
   * @return the stored link
   */
  public CalendarLink save(long calendarId, long creatorId, String tokenHash, String tokenEncrypted, Date createdDate) {
    CalendarLinkEntity entity = calendarLinkDAO.findByCalendarId(calendarId);
    if (entity == null) {
      entity = new CalendarLinkEntity();
      entity.setCalendarId(calendarId);
      fill(entity, creatorId, tokenHash, tokenEncrypted, createdDate);
      try {
        return toModel(calendarLinkDAO.saveAndFlush(entity));
      } catch (DataIntegrityViolationException | PersistenceException e) {
        entity = calendarLinkDAO.findByCalendarId(calendarId);
        if (entity == null) {
          throw e;
        }
      }
    }
    fill(entity, creatorId, tokenHash, tokenEncrypted, createdDate);
    return toModel(calendarLinkDAO.save(entity));
  }

  /**
   * Deletes the link of a calendar.
   *
   * @param calendarId technical identifier of the calendar
   * @return true when a link was deleted
   */
  public boolean deleteByCalendarId(long calendarId) {
    CalendarLinkEntity entity = calendarLinkDAO.findByCalendarId(calendarId);
    if (entity == null) {
      return false;
    }
    calendarLinkDAO.delete(entity);
    return true;
  }

  /**
   * Copies the fields a creation and a reset both write.
   *
   * @param entity the row to fill
   * @param creatorId identity identifier of the creator
   * @param tokenHash digest of the token
   * @param tokenEncrypted encrypted token
   * @param createdDate creation date
   */
  private void fill(CalendarLinkEntity entity, long creatorId, String tokenHash, String tokenEncrypted, Date createdDate) {
    entity.setCreatorId(creatorId);
    entity.setTokenHash(tokenHash);
    entity.setTokenEncrypted(tokenEncrypted);
    entity.setCreatedDate(createdDate);
  }

  /**
   * Maps a row to its model; {@code token} and {@code active} are left unset,
   * they are the service's to compute.
   *
   * @param entity the row, may be null
   * @return the model, or null for no row
   */
  private CalendarLink toModel(CalendarLinkEntity entity) {
    if (entity == null) {
      return null;
    }
    return new CalendarLink(entity.getCalendarId(),
                            entity.getCreatorId(),
                            entity.getCreatedDate() == null ? 0 : entity.getCreatedDate().getTime(),
                            entity.getTokenHash(),
                            entity.getTokenEncrypted(),
                            null,
                            false);
  }

}
