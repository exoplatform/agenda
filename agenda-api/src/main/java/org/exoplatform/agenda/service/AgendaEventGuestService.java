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
package org.exoplatform.agenda.service;

import org.exoplatform.agenda.model.*;

import java.util.List;

public interface AgendaEventGuestService {

    /**
     * Return the list of guests of an event .
     *
     * @param eventId agenda {@link Event} identifier
     * @return {@link List} of {@link GuestUser}
     */
    List<GuestUser> getEventGuests(long eventId);

    /**
     * @param eventId {@link Event} to attach guests
     * @param guests {@link List} of {@link GuestUser} to save for event
     *          event
    */
    void saveEventGuests(long eventId, List<GuestUser> guests);


    void deleteEventGuests(long eventId);

    boolean isGuest(String user,String guests,long eventId);

}
