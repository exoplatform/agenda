<template>
  <div>
    <agenda-recurrent-event-save-confirm-dialog
      ref="recurrentEventConfirm"
      @save-event="save"
      @dialog-opened="$root.$emit('agenda-event-save-opened')"
      @dialog-closed="$root.$emit('agenda-event-save-cancel')" />
    <agenda-capacity-close-confirm-dialog ref="closeConfirm" />
  </div>
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
    // The event as the form opened it, by the event object the form edits in
    // place: its participants, as a wider scope applies the form's change to
    // the series' own list, not the date's list, and whether it was open with
    // a limit, which a save closing it confirms first (D15)
    this.asOpened = new WeakMap();
    this.confirmClose = false;
    this.$root.$on('agenda-event-form-opened', this.rememberAsOpened);
    this.$root.$on('agenda-event-save', this.saveEvent);
  },
  methods: {
    rememberAsOpened(event) {
      if (event) {
        this.asOpened.set(event, {
          attendees: JSON.parse(JSON.stringify(event.attendees || [])),
          limited: !!event.open && event.capacity > 0,
        });
      }
    },
    saveEvent(event, ignoreRecurrentPopin, changeDatesOnly) {
      this.confirmClose = false;
      if (!this.closesWithLimit(event)) {
        this.saveInScope(event, ignoreRecurrentPopin, changeDatesOnly);
        return;
      }
      // Asked before the scope, whose save then carries the answer
      this.$refs.closeConfirm.confirm()
        .then(() => {
          this.confirmClose = true;
          this.saveInScope(event, ignoreRecurrentPopin, changeDatesOnly);
        })
        .catch(() => this.$root.$emit('agenda-event-save-cancel'));
    },
    saveInScope(event, ignoreRecurrentPopin, changeDatesOnly) {
      if (event.occurrence && !ignoreRecurrentPopin) {
        const asOpened = this.asOpened.get(event);
        const attendeesDelta = asOpened ? this.$agendaUtils.attendeesDelta(asOpened.attendees, event.attendees) : null;
        this.$refs.recurrentEventConfirm.open(event, changeDatesOnly, attendeesDelta);
      } else {
        this.save(event, changeDatesOnly);
      }
    },
    /**
     * @param {Object} event the event the form saves
     * @returns {Boolean} whether the save closes an event the form opened open
     *          with a limit: its padlock locked, or the event moved to a
     *          personal calendar. A switch to a date poll is no closing: it
     *          confirms nobody
     */
    closesWithLimit(event) {
      const asOpened = this.asOpened.get(event);
      if (!asOpened || !asOpened.limited || this.isDatePoll(event)) {
        return false;
      }
      return !event.open || !this.isSpaceCalendarEvent(event);
    },
    isSpaceCalendarEvent(event) {
      const owner = event.calendar && event.calendar.owner;
      return !!(owner && (owner.providerId === 'space' || !!owner.space));
    },
    isDatePoll(event) {
      return event.status === 'TENTATIVE' || !!(event.dateOptions && event.dateOptions.length > 1);
    },
    save(eventToSave, changeDatesOnly) {
      this.saving = true;
      this.$refs.closeConfirm.writeConfirmingClose(confirmClose => this.write(eventToSave, changeDatesOnly, confirmClose), this.confirmClose)
        .then(event => this.saved(event || eventToSave))
        .catch(error => this.saveFailed(eventToSave, error))
        .finally(() => {
          this.saving = false;
        });
    },
    write(eventToSave, changeDatesOnly, confirmClose) {
      if (eventToSave.id && changeDatesOnly) {
        return this.$eventService.updateEventFields(eventToSave, {
          start: this.$agendaUtils.toRFC3339(eventToSave.start, false, true),
          end: this.$agendaUtils.toRFC3339(eventToSave.end, false, true),
          timeZoneId: this.$agendaUtils.USER_TIMEZONE_ID,
        }, !!eventToSave.recurrence, changeDatesOnly, confirmClose);
      }
      return eventToSave.id ? this.$eventService.updateEvent(eventToSave, confirmClose) : this.$eventService.createEvent(eventToSave);
    },
    saved(event) {
      this.$refs.recurrentEventConfirm.close();
      this.$root.$emit('agenda-event-saved', event);
      this.$root.$emit('agenda-refresh');
    },
    saveFailed(eventToSave, error) {
      if (!error) {
        this.$refs.recurrentEventConfirm.close();
        this.$root.$emit('agenda-event-save-cancel');
        return;
      }
      this.$root.$emit('agenda-event-save-error', eventToSave, error);
      // A value the event refuses (a limit below its Yes answers, a limit
      // where none is allowed) is said, with the date of a series that blocks
      // when the refusal names one; anything else keeps the generic path
      if (error.code && error.date && this.$te(`${error.code}OnDate`)) {
        this.$root.$emit('alert-message', this.$t(`${error.code}OnDate`, {0: this.$agendaUtils.formatDay(error.date)}), 'error');
      } else if (error.code && this.$te(error.code)) {
        this.$root.$emit('alert-message', this.$t(error.code), 'error');
      }
    },
  },
};
</script>
