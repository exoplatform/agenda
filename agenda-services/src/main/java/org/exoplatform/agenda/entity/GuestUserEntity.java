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
package org.exoplatform.agenda.entity;

import org.exoplatform.commons.api.persistence.ExoEntity;
import javax.persistence.*;
import java.io.Serializable;

@Entity(name = "AgendaEventGuest")
@ExoEntity
@Table(name = "EXO_AGENDA_GUESTS")
@NamedQuery(
       name = "AgendaEventGuest.deleteEventGuests",
       query = "DELETE FROM AgendaEventGuest a WHERE a.event.id = :eventId")
@NamedQuery(
         name = "AgendaEventGuest.deleteGuest",
         query = "DELETE FROM AgendaEventGuest a WHERE a.event.id = :eventId AND  a.id = :id")
@NamedQuery(
          name = "AgendaEventGuest.getEventGuestsByEventId",
          query = "SELECT a FROM AgendaEventGuest a"
          + "  WHERE a.event.id = :eventId")


public class GuestUserEntity implements Serializable {

    private static final long     serialVersionUID = 4013389950407427033L;

    @Id
    @SequenceGenerator(name = "SEQ_AGENDA_EVENT_GUEST_ID", sequenceName = "SEQ_AGENDA_EVENT_GUEST_ID", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_AGENDA_EVENT_GUEST_ID")
    @Column(name = "EVENT_GUEST_ID")
    private Long                  id;

    @Column(name = "GUEST_EMAIL", nullable = false)
    private String           guestEmail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EVENT_ID", referencedColumnName = "EVENT_ID")
    private EventEntity           event;

    public void setId(Long id) {
        this.id = id;
    }

    public void setGuestEmail(String guestEmail) {
        this.guestEmail = guestEmail;
    }

    public void setEvent(EventEntity event) {
        this.event = event;
    }

    public Long getId() {
        return id;
    }

    public String getGuestEmail() {
        return guestEmail;
    }

    public EventEntity getEvent() {
        return event;
    }

}
