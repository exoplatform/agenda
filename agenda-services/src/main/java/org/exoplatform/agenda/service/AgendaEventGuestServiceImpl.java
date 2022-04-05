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
import org.exoplatform.agenda.storage.AgendaEventGuestStorage;

import java.util.Collections;
import java.util.List;

public class AgendaEventGuestServiceImpl implements AgendaEventGuestService{

    private AgendaEventGuestStorage   agendaEventGuestStorage;

    public AgendaEventGuestServiceImpl(AgendaEventGuestStorage agendaEventGuestStorage) {
        this.agendaEventGuestStorage = agendaEventGuestStorage;
    }

    @Override
    public List<GuestUser> getEventGuests(long eventId) {
        return agendaEventGuestStorage.getEventGuests(eventId);
    }

    @Override
    public void saveEventGuests(long eventId, List<GuestUser> guests) {
        List<GuestUser> oldGuests = getEventGuests(eventId);
        List<GuestUser> newGuests = guests == null ? Collections.emptyList() : guests;
        processGuestsToDelete(oldGuests, newGuests, eventId);
        processGuestsToCreate(oldGuests, newGuests, eventId);
    }

    @Override
    public void deleteEventGuests(long eventId) {
       agendaEventGuestStorage.deleteEventGuests(eventId);
    }

    public void processGuestsToCreate(List<GuestUser> oldGuests,List<GuestUser> newGuests,Long eventId){
        newGuests.stream().filter(guest -> !oldGuests.contains(guest)).forEach(guest -> agendaEventGuestStorage.saveEventGuestUser(guest, eventId));
    }
    public void processGuestsToDelete(List<GuestUser> oldGuests,List<GuestUser> newGuests, long eventId) {
        oldGuests.stream().filter(guest -> !newGuests.contains(guest)).forEach(guest -> agendaEventGuestStorage.deleteGuest(eventId, guest.getId()));
    }
}

