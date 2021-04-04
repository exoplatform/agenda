<template>
  <v-dialog
    v-model="dialog"
    content-class="uiPopup width-auto"
    width="300">
    <v-card class="elevation-12">
      <div class="ignore-vuetify-classes popupHeader ClearFix">
        <a
          class="uiIconClose pull-right"
          aria-hidden="true"
          @click="close"></a>
        <span class="ignore-vuetify-classes PopupTitle popupTitle text-truncate">
          {{ $t('agenda.confirmCancelRecurrentEventTitle') }}
        </span>
      </div>
      <v-card-text>
        <v-radio-group v-model="recurrenceModificationType">
          <v-radio :label="$t('agenda.onlyThisEvent')" value="single" />
          <v-radio :label="$t('agenda.thisAndUpcomingEvents')" value="upcoming" />
          <v-radio :label="$t('agenda.allEvents')" value="all" />
        </v-radio-group>
      </v-card-text>
      <v-card-actions class="d-flex flex-wrap justify-center center">
        <button
          :disabled="loading"
          :loading="loading"
          class="ignore-vuetify-classes btn me-2 mb-1"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </button>
        <button
          :disabled="loading"
          :loading="loading"
          class="ignore-vuetify-classes btn-primary ms-2 mb-1"
          @click="deleteRecurrentEventChoice">
          {{ $t('agenda.button.save') }}
        </button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: function() {
        return null;
      },
    },
  },
  data: () => ({
    loading: false,
    recurrenceModificationType: 'single',
    dialog: false,
  }),
  watch: {
    dialog() {
      if (this.dialog) {
        this.$emit('dialog-opened');
      } else {
        this.$emit('dialog-closed');
      }
    },
  },
  created() {
    $(document).on('keydown', (event) => {
      if (event.key === 'Escape') {
        this.dialog = false;
      }
    });
  },
  methods: {
    deleteRecurrentEventChoice(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }
      if (this.recurrenceModificationType === 'single') {
        this.deleteOccurrenceEvent();
      } else if (this.recurrenceModificationType === 'all') {
        this.deleteRecurrentEvent();
      } else if (this.recurrenceModificationType === 'upcoming') {
        this.deleteUpcomingEvents();
      }
    },
    deleteUpcomingEvents(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }

      let recurrentEvent = this.event.parent;
      return this.$eventService.getEventById(recurrentEvent.id, 'all')
        .then(parentRecurrentEvent => {
          recurrentEvent = JSON.parse(JSON.stringify(parentRecurrentEvent));
          const untilDate = new Date(this.event.occurrence.id);
          untilDate.setDate(untilDate.getDate() - 1);
          untilDate.setHours(23);
          untilDate.setMinutes(59);
          untilDate.setSeconds(59);

          const startDate = new Date(recurrentEvent.start);

          // If the deleted occurrence is the first occurrence of the recurring event
          // Then delete all current recurrent event
          if (startDate >= untilDate) {
            return this.deleteRecurrentEvent();
          } else {
            parentRecurrentEvent.recurrence.until = this.$agendaUtils.toRFC3339(untilDate);
            return this.$eventService.updateEvent(parentRecurrentEvent)
              .then((updatedEvent) => {
                this.$root.$emit('agenda-event-deleted', updatedEvent, true);
                this.close();
              });
          }
        });
    },
    deleteRecurrentEvent(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }

      this.loading = true;
      return this.$eventService.deleteEvent(this.event.parent.id)
        .then(() => {
          this.$root.$emit('agenda-event-deleted', this.event.parent);
          this.close();
        })
        .finally(() => this.loading = false);
    },
    deleteOccurrenceEvent(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }

      let saveEventMethod = null;
      if (this.event.id) {
        saveEventMethod = this.$eventService.updateEvent;
      } else {
        saveEventMethod = this.$eventService.createEvent;
      }
      const eventToDelete = JSON.parse(JSON.stringify(this.event));
      delete eventToDelete.recurrence;
      eventToDelete.status = 'CANCELLED';

      this.loading = true;
      return saveEventMethod(eventToDelete)
        .then(event => {
          this.$root.$emit('agenda-event-deleted', event);
          this.dialog = false;
        })
        .finally(() => this.loading = false);
    },
    close(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }

      this.dialog = false;
    },
    open() {
      this.dialog = true;
    },
  },
};
</script>