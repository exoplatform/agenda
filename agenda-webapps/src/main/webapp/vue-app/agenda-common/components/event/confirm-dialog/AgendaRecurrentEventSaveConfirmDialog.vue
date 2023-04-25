<template>
  <v-dialog
    v-model="dialog"
    content-class="uiPopup"
    width="300">
    <v-card class="elevation-12">
      <div class="ignore-vuetify-classes popupHeader ClearFix">
        <a
          class="uiIconClose pull-right"
          aria-hidden="true"
          @click="close"></a>
        <span class="ignore-vuetify-classes PopupTitle popupTitle text-truncate">
          {{ $t('agenda.confirmSaveRecurrentEventTitle') }}
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
          @click="saveRecurrentEventChoice">
          {{ $t('agenda.button.save') }}
        </button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script>
export default {
  data: () => ({
    event: null,
    changeDatesOnly: false,
    recurrenceModificationType: 'single',
    loading: false,
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
    saveRecurrentEventChoice(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }
      if (this.recurrenceModificationType === 'single') {
        this.saveOccurrenceEvent();
      } else if (this.recurrenceModificationType === 'all') {
        this.saveRecurrentEvent();
      } else if (this.recurrenceModificationType === 'upcoming') {
        this.saveUpcomingEvents();
      }
    },
    saveUpcomingEvents(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }

      let recurrentEvent = this.event.parent;
      this.$eventService.getEventById(recurrentEvent.id, 'all')
        .then(parentRecurrentEvent => {
          recurrentEvent = JSON.parse(JSON.stringify(parentRecurrentEvent));
          const untilDate = new Date(this.event.occurrence.id);
          untilDate.setDate(untilDate.getDate() - 1);
          untilDate.setHours(23);
          untilDate.setMinutes(59);
          untilDate.setSeconds(59);
          const startDate = new Date(recurrentEvent.start);

          // If the modified occurrence is the first occurrence of the recurring event
          // Then change all current recurrent event
          if (startDate >= untilDate) {
            return this.saveRecurrentEvent();
          } else {
            parentRecurrentEvent.recurrence.until = this.$agendaUtils.toRFC3339(untilDate);
            parentRecurrentEvent.sendInvitation = false;
            return this.$eventService.updateEvent(parentRecurrentEvent)
              .then(() => {
                recurrentEvent.start = this.event.start;
                recurrentEvent.end = this.event.end;
                const day = this.event.start;
                const eventRecurrence = this.event && this.event.recurrence || this.event.parent && this.event.parent.recurrence;
                const recurrenceType = eventRecurrence && eventRecurrence.type || 'NO_REPEAT';
                if (recurrenceType === 'WEEKLY') {
                  const dayNameFromDate  = this.$agendaUtils.getDayNameFromDate(this.event.start);
                  recurrentEvent.recurrence.byDay = [dayNameFromDate.substring(0, 2).toUpperCase()];
                }
                this.$agendaUtils.getDayNameFromDate(day, eXo.env.portal.language);
                delete recurrentEvent.id;
                return this.$eventService.createEvent(recurrentEvent);
              })
              .then(() => {
                this.close();
                this.$root.$emit('agenda-event-saved', recurrentEvent);
              });
          }
        });
    },
    saveRecurrentEvent(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }

      const eventToSave = JSON.parse(JSON.stringify(this.event));
      eventToSave.id = this.event.parent.id;
      eventToSave.remoteId = eventToSave.parent && eventToSave.parent.remoteId || '';
      eventToSave.remoteProviderId = eventToSave.parent && eventToSave.parent.remoteProviderId || 0;
      eventToSave.recurrence = this.event.recurrence || this.event.parent.recurrence;
      eventToSave.occurrence = null;
      eventToSave.parent = null;
      // Keep same original recurrent event dates, and change only time
      eventToSave.start = this.$agendaUtils.getSameTime(this.event.parent.start, this.event.start);
      eventToSave.end = this.$agendaUtils.getSameTime(this.event.parent.end, this.event.end);

      this.$emit('save-event', eventToSave, this.changeDatesOnly);
    },
    saveOccurrenceEvent(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }

      const eventToSave = JSON.parse(JSON.stringify(this.event));
      this.$emit('save-event', eventToSave, this.changeDatesOnly);
    },
    close(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }

      this.dialog = false;
    },
    open(event, changeDatesOnly) {
      this.event = event;
      this.recurrenceModificationType = 'single';
      this.changeDatesOnly = changeDatesOnly;
      this.dialog = true;
    },
  },
};
</script>