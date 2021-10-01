<!--
SPDX-FileCopyrightText: 2021 eXo Platform SAS

SPDX-License-Identifier: AGPL-3.0-only
-->

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: null
    },
    currentCalendar: {
      type: Object,
      default: () => null,
    },
    calendarType: {
      type: String,
      default: null
    },
    ownerIds: {
      type: Array,
      default: () => null,
    },
    events: {
      type: Array,
      default: null
    },
    period: {
      type: Object,
      default: () => null,
    },
    limit: {
      type: Number,
      default: () => 0,
    },
  },
  created() {
    this.$agendaWebSocket.initCometd(this.settings.cometdContext, this.settings.cometdToken, this.handleAgendaUpdates);
  },
  methods: {
    handleAgendaUpdates(wsEventName, eventModifications) {
      if (!eventModifications) {
        return;
      }
      const calendarId = eventModifications.calendarId;
      const eventId = eventModifications.eventId;

      if (this.currentCalendar && this.currentCalendar.id !== calendarId) {
        return;
      }

      if (wsEventName === 'exo.agenda.event.deleted') {
        this.deleteEvent(eventId);
      } else {
        this.retrieveAndAddEvent(eventId);
      }
    },
    deleteEvent(eventId) {
      if (this.events && this.events.length) {
        let i = 0;
        while (i < this.events.length) {
          const existingEventIndex = this.events.findIndex(event => event.id === eventId || (event.parent && event.parent.id === eventId));
          if (existingEventIndex >= 0) {
            this.events.splice(existingEventIndex, 1);
          } else {
            i++;
          }
        }
      }
      this.$root.$emit('agenda-refresh-pending');
    },
    retrieveAndAddEvent(eventId) {
      this.$eventService.getEventById(eventId, 'all')
        .then(updatedEvent => {
          if (!updatedEvent) {
            return;
          }
          if (this.ownerIds && this.ownerIds.length && this.ownerIds.indexOf(updatedEvent.calendar.owner.id) < 0) {
            return;
          } else if (updatedEvent.status === 'TENTATIVE' || updatedEvent.status === 'CANCELLED') {
            this.deleteEvent(eventId);
          } else if (updatedEvent.recurrence && !updatedEvent.occurrence) {
            // When a new recurrent event is updated / created
            // The computing in Frontend is too complex
            // So just refresh the list of events for current user
            this.$root.$emit('agenda-refresh');
          } else {
            // Update, add or hide event
            this.updateEventsView(updatedEvent);
          }
        });
    },
    updateEventsView(updatedEvent) {
      // Retrieve event index from displayed events list
      const existingEventIndex = this.events.findIndex(event => {
        return (event.id === updatedEvent.id) || (event.parent && updatedEvent.parent
                                                  && event.parent.id === updatedEvent.parent.id
                                                  && event.occurrence.id === updatedEvent.occurrence.id);
      });
      // Retrieve existing event from displayed events list
      const existingEvent = existingEventIndex >= 0 && this.events[existingEventIndex];
      // Get current user answer to updated event (new response)
      const currentAttendee = updatedEvent.attendees.find(attendee => attendee.identity.id === eXo.env.portal.userIdentityId);
      if (this.calendarType !== 'allEvents'
          && (!currentAttendee
            || this.calendarType === 'declinedEvent' && currentAttendee.response !== 'DECLINED'
            || this.calendarType === 'myEvents' && currentAttendee.response === 'DECLINED')) {
        // Here the user is displaying a list of events
        // where the updated event shouldn't be displayed
        if (currentAttendee && currentAttendee.response === 'NEEDS_ACTION') {
          this.$root.$emit('agenda-refresh-pending');
        }
        if (existingEvent) {
          this.events.splice(existingEventIndex, 1);
        }
      } else if (existingEvent) {
        // Check whether we need to update badge or not
        const previousAttendee = existingEvent.attendees && existingEvent.attendees.find(attendee => attendee.identity.id === eXo.env.portal.userIdentityId);
        if (currentAttendee && currentAttendee.response === 'NEEDS_ACTION'
            || (previousAttendee
            && (existingEvent.status === 'TENTATIVE' || previousAttendee.response === 'NEEDS_ACTION'))) {
          this.$root.$emit('agenda-refresh-pending');
        }

        // Replace existing event by the new one
        this.events.splice(existingEventIndex, 1, updatedEvent);
      } else {
        // Check whether we need to update badge or not
        if (currentAttendee && currentAttendee.response === 'NEEDS_ACTION') {
          this.$root.$emit('agenda-refresh-pending');
        }
        // If we are in Widget, we should avoid adding events when the limit
        // of events to display is already reached
        if (this.limit && this.events && this.events.length >= this.limit) {
          return;
        }

        // Add a new event in the list of events
        this.events.push(updatedEvent);
      }
    },
  },
};
</script>