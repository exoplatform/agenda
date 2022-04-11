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
package org.exoplatform.agenda.storage;

import org.exoplatform.agenda.dao.EventDAO;
import org.exoplatform.agenda.dao.GuestUserDAO;
import org.exoplatform.agenda.entity.EventEntity;
import org.exoplatform.agenda.entity.GuestUserEntity;
import org.exoplatform.agenda.model.GuestUser;
import org.exoplatform.agenda.util.EntityMapper;

import java.util.ArrayList;
import java.util.List;

public class AgendaEventGuestStorage {

    private EventDAO eventDAO;

    private GuestUserDAO guestUserDAO;

    public AgendaEventGuestStorage(EventDAO eventDAO,
                                   GuestUserDAO guestUserDAO) {
        this.eventDAO = eventDAO;
        this.guestUserDAO = guestUserDAO;
    }

    public void saveEventGuestUser(GuestUser guestUser, long eventId) {
        EventEntity eventEntity = eventDAO.find(eventId);
        GuestUserEntity guestUserEntity = EntityMapper.toEntity(guestUser,eventEntity);
        guestUserEntity.setId(null);
        guestUserEntity = guestUserDAO.create(guestUserEntity);
        long eventGuestId = guestUserEntity.getId();
        guestUser.setId(eventGuestId);
    }

    public List<GuestUser> getEventGuests(long eventId) {
        List<GuestUserEntity> guestUserEntities = guestUserDAO.getEventGuests(eventId);
        List<GuestUser> guestUserList = new ArrayList<>();
        for (GuestUserEntity guestUserEntity : guestUserEntities) {
            GuestUser guestUser = EntityMapper.fromEntity(guestUserEntity,eventId);
            guestUserList.add(guestUser);
        }
        return guestUserList;
    }

    public  void deleteGuest(long eventId, long guestId){
        guestUserDAO.deleteGuest(eventId,guestId);

    }
    public void deleteEventGuests(long eventId) {
        guestUserDAO.deleteEventGuests(eventId);
    }
}
