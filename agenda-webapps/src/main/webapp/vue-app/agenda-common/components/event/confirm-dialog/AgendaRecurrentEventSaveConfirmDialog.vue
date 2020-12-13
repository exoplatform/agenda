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
      <v-card-actions class="d-flex flex-wrap justify-center center">
        <button
          :disabled="loading"
          :loading="loading"
          class="ignore-vuetify-classes btn mr-2 mb-1"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </button>
        <button
          :disabled="loading"
          :loading="loading"
          class="ignore-vuetify-classes btn ml-2 mb-1"
          @click="saveRecurrentEvent">
          {{ $t('agenda.button.saveRecurrentEvent') }}
        </button>
        <button
          :disabled="loading"
          :loading="loading"
          class="ignore-vuetify-classes btn-primary ml-2 mb-1"
          @click="saveOccurrenceEvent">
          {{ $t('agenda.button.saveOccurrenceEvent') }}
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
      eventObject.preventDefault();
      eventObject.stopPropagation();

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
      this.changeDatesOnly = changeDatesOnly;
      this.dialog = true;
    },
  },
};
</script>