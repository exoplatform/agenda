<template>
  <agenda-recurrent-event-save-confirm-dialog
    ref="recurrentEventConfirm"
    @save-event="save"
    @dialog-opened="$root.$emit('agenda-event-save-opened')"
    @dialog-closed="$root.$emit('agenda-event-save-cancel')" />
</template>

<script>
export default {
  data () {
    return {
      event: null,
      saving: false,
    };
  },
  created() {
    // The participants of an event as the form opened it, by the event object
    // the form edits in place: a wider scope applies the form's change to the
    // series' own list, not the date's list
    this.attendeesAsOpened = new WeakMap();
    this.$root.$on('agenda-event-form-opened', this.rememberAttendees);
    this.$root.$on('agenda-event-save', this.saveEvent);
  },
  methods: {
    rememberAttendees(event) {
      if (event) {
        this.attendeesAsOpened.set(event, JSON.parse(JSON.stringify(event.attendees || [])));
      }
    },
    saveEvent(event, ignoreRecurrentPopin, changeDatesOnly) {
      if (event.occurrence && !ignoreRecurrentPopin) {
        const attendeesAsOpened = this.attendeesAsOpened.get(event);
        const attendeesDelta = attendeesAsOpened ? this.$agendaUtils.attendeesDelta(attendeesAsOpened, event.attendees) : null;
        this.$refs.recurrentEventConfirm.open(event, changeDatesOnly, attendeesDelta);
      } else {
        this.save(event, changeDatesOnly);
      }
    },
    save(eventToSave, changeDatesOnly) {
      this.saving = true;
      if (eventToSave.id && changeDatesOnly) {
        this.$eventService.updateEventFields(eventToSave, {
          start: this.$agendaUtils.toRFC3339(eventToSave.start, false, true),
          end: this.$agendaUtils.toRFC3339(eventToSave.end, false, true),
          timeZoneId: this.$agendaUtils.USER_TIMEZONE_ID,
        }, !!eventToSave.recurrence, changeDatesOnly)
          .then(event => this.saved(event || eventToSave))
          .catch(error => this.$root.$emit('agenda-event-save-error', event, error))
          .finally(() => {
            this.saving = false;
          });
      } else {
        const saveEventMethod = eventToSave.id ? this.$eventService.updateEvent : this.$eventService.createEvent;
        saveEventMethod(eventToSave)
          .then(event => this.saved(event || eventToSave))
          .catch(error => this.$root.$emit('agenda-event-save-error', event, error))
          .finally(() => {
            this.saving = false;
          });
      }
    },
    saved(event) {
      this.$refs.recurrentEventConfirm.close();
      this.$root.$emit('agenda-event-saved', event);
      this.$root.$emit('agenda-refresh');
    },
  },
};
</script>
