/*
 * Copyright (C) 2022 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.dao;

import org.exoplatform.agenda.entity.GuestUserEntity;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;

import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.Collections;
import java.util.List;

public class GuestUserDAO extends GenericDAOJPAImpl<GuestUserEntity, Long> {

    private static final String EVENT_ID = "eventId";

    @ExoTransactional
    public void deleteEventGuests(long eventId) {
        Query deleteEventsQuery = getEntityManager().createNamedQuery("AgendaEventGuest.deleteEventGuests");
        deleteEventsQuery.setParameter(EVENT_ID, eventId);
        deleteEventsQuery.executeUpdate();
    }

    public List<GuestUserEntity> getEventGuests(long eventId) {
        TypedQuery<GuestUserEntity> query = getEntityManager().createNamedQuery("AgendaEventGuest.getEventGuestsByEventId",
                GuestUserEntity.class);
        query.setParameter(EVENT_ID, eventId);
        List<GuestUserEntity> resultList = query.getResultList();
        return resultList == null ? Collections.emptyList() : resultList;
    }
    @ExoTransactional
    public void deleteGuest(long eventId, long guestId) {
        Query deleteGuestsQuery = getEntityManager().createNamedQuery("AgendaEventGuest.deleteGuest");
        deleteGuestsQuery.setParameter(EVENT_ID, eventId);
        deleteGuestsQuery.setParameter("id", guestId);
        deleteGuestsQuery.executeUpdate();
    }


}
