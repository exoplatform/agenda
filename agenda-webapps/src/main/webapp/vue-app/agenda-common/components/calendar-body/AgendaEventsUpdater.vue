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
        if (!this.events || !this.events.length) {
          const existingEventIndex = this.events.findIndex(event => event.id === eventId);
          if (existingEventIndex >= 0) {
            this.events.splice(existingEventIndex, 1);
          }
        }
      } else {
        this.retrieveAndAddEvent(eventId);
      }
    },
    deleteEventByIndex(existingEventIndex) {
      const deletedEvents = this.events.splice(existingEventIndex, 1);
      if (deletedEvents && deletedEvents.length) {
        // When deleting an event, the pending events may have to be
        // updated
        this.$root.$emit('agenda-refresh-pending');
      }
    },
    deleteEventById(eventId) {
      if (!this.events || !this.events.length) {
        const existingEventIndex = this.events.findIndex(event => event.id === eventId);
        if (existingEventIndex >= 0) {
          this.deleteEventByIndex(existingEventIndex);
        }
      }
    },
    retrieveAndAddEvent(eventId) {
      this.$eventService.getEventById(eventId, 'all')
        .then(updatedEvent => {
          if (!updatedEvent) {
            return;
          }
          if (this.ownerIds && this.ownerIds.length && this.ownerIds.indexOf(Number(updatedEvent.calendar.owner.id)) < 0) {
            return;
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
        if (event.id === updatedEvent.id) {
          return true;
        }
        if (event.parent && updatedEvent.parent
            && event.parent.id === updatedEvent.parent.id
            && event.occurrence.id === updatedEvent.occurrence.id) {
          return true;
        }
        return false;
      });
      // Retrieve existing event from displayed events list
      const existingEvent = existingEventIndex >= 0 && this.events[existingEventIndex];
      // Get current user answer to updated event (new response)
      const currentAttendee = updatedEvent.attendees.find(attendee => attendee.identity.id === eXo.env.portal.userIdentityId);
      if (existingEvent && updatedEvent.status !== 'CONFIRMED') {
        // When the event is canceled or turned into a date poll
        // just hide it from UI
        this.events.splice(existingEventIndex, 1);
        if (updatedEvent.status === 'TENTATIVE') {
          // if the event was displayed and the event is
          // changed to a date poll, then refresh pending events
          // badge
          this.$root.$emit('agenda-refresh-pending');
        }
        return;
      } else if (this.calendarType !== 'allEvents'
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
        return;
      }

      if (existingEvent) {
        // Check whether we need to update badge or not
        const previousAttendee = existingEvent.attendees && existingEvent.attendees.find(attendee => attendee.identity.id === eXo.env.portal.userIdentityId);
        if (currentAttendee && currentAttendee.response === 'NEEDS_ACTION'
            || (previousAttendee
            && (previousAttendee.status === 'TENTATIVE'
              || previousAttendee.response === 'NEEDS_ACTION'))) {
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