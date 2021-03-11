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
    this.$root.$on('agenda-event-save', this.saveEvent);
  },
  methods: {
    saveEvent(event, ignoreRecurrentPopin, changeDatesOnly) {
      if (event.occurrence && !ignoreRecurrentPopin) {
        this.$refs.recurrentEventConfirm.open(event, changeDatesOnly);
      } else {
        this.save(event, changeDatesOnly);
      }
    },
    save(eventToSave, changeDatesOnly) {
      this.saving = true;
      if (eventToSave.id && changeDatesOnly) {
        this.$eventService.updateEventFields(eventToSave.id, {
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
    },
  },
};
</script>