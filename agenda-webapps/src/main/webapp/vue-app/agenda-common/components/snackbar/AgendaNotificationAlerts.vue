<!--
SPDX-FileCopyrightText: 2021 eXo Platform SAS

SPDX-License-Identifier: AGPL-3.0-only
-->

<template>
  <v-snackbar
    :value="displayAlerts"
    color="transparent"
    elevation="0"
    absolute
    app
    left>
    <agenda-notification-alert
      v-for="alert in alerts"
      :key="alert.message"
      :alert="alert"
      @dismissed="deleteAlert(alert)" />
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
  }),
  computed: {
    displayAlerts() {
      return this.alerts && this.alerts.length;
    },
  },
  created() {
    this.$root.$on('agenda-notification-alert', this.addAlert);
    const self = this;
    this.$root.$on('agenda-event-saved', (event, name) => {
      if (name !== self.name && (self.name || !self.name !== !name)) {
        return;
      }
      if (event && event.id) {
        const isDatePoll = event.status === 'TENTATIVE';
        const isNew = !event.updated;

        const message = isDatePoll && (isNew && self.$t('agenda.datePollCreationSuccess') || self.$t('agenda.datePollUpdateSuccess'))
                     || (isNew && self.$t('agenda.eventCreationSuccess') || self.$t('agenda.eventUpdateSuccess'));
        const clickMessage = isDatePoll && self.$t('agenda.viewDatePoll') || self.$t('agenda.viewEvent');
        self.addAlert({
          message,
          type: 'success',
          click: () => self.$root.$emit('agenda-event-details', event),
          clickMessage,
        });
      }
    });
    self.$root.$on('agenda-event-deleted', (event, untilDateRecurrenceUpdated) => {
      if (event && event.id) {
        const isDatePoll = event.status === 'TENTATIVE';
        const message = isDatePoll
                        && self.$t('agenda.datePollDeleteSuccess')
                        || (untilDateRecurrenceUpdated && self.$t('agenda.eventRecurrenceUntilDateUpdated'))
                        || self.$t('agenda.eventDeleteSuccess');
        const deleteEventAlert = {
          message,
          type: 'success',
        };
        if (isDatePoll || !untilDateRecurrenceUpdated) {
          deleteEventAlert.clickMessage = self.$t('agenda.undoRemoveEvent');
          deleteEventAlert.click = () => self.undoDeleteEvent(event, deleteEventAlert);
        }
        self.addAlert(deleteEventAlert);
      }
    });
  },
  methods: {
    addAlert(alert) {
      if (alert) {
        this.alerts.push(alert);
        window.setTimeout(() => this.deleteAlert(alert), 5000);
      }
    },
    deleteAlert(alert) {
      const index = this.alerts.indexOf(alert);
      this.alerts.splice(index, 1);
      this.$forceUpdate();
    },
    undoDeleteEvent(event, alert) {
      if (event.occurrence && event.occurrence.id) {
        return this.$eventService.updateEventFields(event.id, {status: 'CONFIRMED'}, false, true)
          .then(() => {
            this.$root.$emit('agenda-refresh', event);
            this.deleteAlert(alert);
            this.addAlert({
              message: this.$t('agenda.eventDeletionCanceled'),
              type: 'success',
            });
          });
      } else {
        return this.$eventService.undoDeleteEvent(event.id)
          .then(() => {
            this.$root.$emit('agenda-refresh', event);
            this.deleteAlert(alert);
            this.addAlert({
              message: this.$t('agenda.eventDeletionCanceled'),
              type: 'success',
            });
          });
      }
    }
  },
};
</script>
