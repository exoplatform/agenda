<template>
  <v-dialog
    v-model="dialog"
    :width="width"
    content-class="uiPopup width-auto"
    max-width="100vw">
    <v-card class="elevation-12">
      <div class="ignore-vuetify-classes popupHeader ClearFix">
        <a
          class="uiIconClose pull-right"
          aria-hidden="true"
          @click="close"></a>
        <span class="ignore-vuetify-classes PopupTitle popupTitle text-truncate">
          {{ $t('agenda.title.confirmSaveRecurrentEvent') }}
        </span>
      </div>
      <v-card-text>
        {{ $t('agenda.message.confirmSaveRecurrentEvent') }}
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <button
          :disabled="loading"
          :loading="loading"
          class="ignore-vuetify-classes btn mr-2"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </button>
        <button
          :disabled="loading"
          :loading="loading"
          class="ignore-vuetify-classes btn ml-2"
          @click="saveRecurrentEvent">
          {{ $t('agenda.button.saveRecurrentEvent') }}
        </button>
        <button
          :disabled="loading"
          :loading="loading"
          class="ignore-vuetify-classes btn-primary ml-2"
          @click="saveOccurrenceEvent">
          {{ $t('agenda.button.saveOccurrenceEvent') }}
        </button>
        <v-spacer />
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script>
export default {
  data: () => ({
    event: null,
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
    saveRecurrentEvent(eventObject) {
      eventObject.preventDefault();
      eventObject.stopPropagation();

      const eventToSave = JSON.parse(JSON.stringify(this.event));
      eventToSave.id = this.event.parent.id;
      eventToSave.recurrence = this.event.recurrence || this.event.parent.recurrence;
      eventToSave.occurrence = null;
      eventToSave.parent = null;
      // Keep same original recurrent event dates, and change only time
      eventToSave.start = this.$agendaUtils.getSameTime(this.event.parent.start, this.event.start);
      eventToSave.end = this.$agendaUtils.getSameTime(this.event.parent.end, this.event.end);

      if (this.event.id) {
        // Delete this exceptional event, then save parent recurrent event
        this.$eventService.deleteEvent(this.event.id)
          .then(() => {
            this.$emit('save-event', eventToSave);
          });
      } else {
        this.$emit('save-event', eventToSave);
      }
    },
    saveOccurrenceEvent(eventObject) {
      eventObject.preventDefault();
      eventObject.stopPropagation();

      const eventToSave = JSON.parse(JSON.stringify(this.event));
      this.$emit('save-event', eventToSave);
    },
    close(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }

      this.dialog = false;
    },
    open(event) {
      this.event = event;
      this.dialog = true;
    },
  },
};
</script>