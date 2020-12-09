<template>
  <agenda-recurrent-event-save-confirm-dialog
    ref="recurrentEventConfirm"
    @save-event="save"
    @dialog-closed="$root.$emit('agenda-event-save-cancel')" />
</template>

<script>
export default {
  data () {
    return {
      selectedTimeZone: null,
      event: null,
      saving: false,
    };
  },
  created() {
    this.$root.$on('agenda-event-save', this.saveEvent);
    this.$root.$on('send-timezone',(data) => {
      this.selectedTimeZone = data;
    });
  },
  methods: {
    saveEvent(event, ignoreRecurrentPopin) {
      if (event.occurrence && !ignoreRecurrentPopin) {
        this.$refs.recurrentEventConfirm.open(event);
      } else {
        this.save(event);
      }
    },
    save(eventToSave) {
      this.saving = true;
      eventToSave.timeZoneId = this.selectedTimeZone;
      const saveEventMethod = eventToSave.id ? this.$eventService.updateEvent:this.$eventService.createEvent;
      saveEventMethod(eventToSave)
        .then(event => this.saved(event))
        .finally(() => {
          this.saving = false;
        });
    },
    saved(event) {
      this.$refs.recurrentEventConfirm.close();
      this.$root.$emit('agenda-event-saved', event);
    },
  },
};
</script>