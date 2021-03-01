<template>
  <v-snackbar
    :value="displayAlerts"
    color="transparent"
    elevation="0"
    absolute
    app
    left>
    <agenda-notification-alert
      v-for="(alert, index) in alerts"
      :key="index"
      :alert="alert"
      @dismissed="deleteAlert(index)" />
  </v-snackbar>
</template>

<script>
export default {
  props: {
    name: {
      type: String,
      default: null
    },
  },
  data: () => ({
    alerts: [],
    confirmDeleteEvent: true,
  }),
  computed: {
    displayAlerts() {
      return this.alerts && this.alerts.length;
    },
  },
  created() {
    this.$root.$on('agenda-notification-alert', alert => this.alerts.push(alert));
    this.$root.$on('agenda-event-saved', (event, name) => {
      if (name !== this.name && (this.name || !this.name !== !name)) {
        return;
      }
      if (event && event.id) {
        const isDatePoll = event.dateOptions && event.dateOptions.length > 1;
        const isNew = !event.updated;
        const message = isDatePoll && (isNew && this.$t('agenda.datePollCreationSuccess') || this.$t('agenda.datePollUpdateSuccess'))
                     || (isNew && this.$t('agenda.eventCreationSuccess') || this.$t('agenda.eventUpdateSuccess'));
        const clickMessage = isDatePoll && this.$t('agenda.viewDatePoll') || this.$t('agenda.viewEvent');
        this.$root.$emit('agenda-notification-alert', {
          message,
          type: 'success',
          click: () => this.$root.$emit('agenda-event-details', event),
          clickMessage,
        });
      }
    });
    this.$root.$on('event-canceled', event => {
      if (event && event.id) {
        const clickMessage = this.$t('agenda.undoRemoveDatePoll');
        const message = this.$t('agenda.datePollDeleteSuccess');
        this.$root.$emit('agenda-notification-alert', {
          message,
          type: 'success',
          click: () => this.undoDeleteEvent(event),
          clickMessage,
        });
      }
    });
  },
  methods: {
    deleteAlert(index) {
      this.alerts.splice(index, 1);
      this.$forceUpdate();
    },
    undoDeleteEvent(event) {
      this.$eventService.updateEventFields(event.id, {
        status : event.status
      }, false, true)
        .then(() => {
          this.confirmDeleteEvent = false;
          this.$root.$emit('undo-event-remove', event);
          this.$root.$emit('agenda-refresh', event);
          this.$root.$emit('confirm-delete-event', this.confirmDeleteEvent);
          this.$root.$emit('agenda-event-details', event);
        });
    }
  },
};
</script>
