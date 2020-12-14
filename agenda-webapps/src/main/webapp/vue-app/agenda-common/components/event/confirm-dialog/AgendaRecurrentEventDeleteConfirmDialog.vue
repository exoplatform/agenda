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
          {{ $t('agenda.title.confirmDeleteEvent') }}
        </span>
      </div>
      <v-card-text>
        {{ $t('agenda.message.confirmDeleteRecurrentEvent') }}
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
          @click="deleteRecurrentEvent">
          {{ $t('agenda.button.deleteRecurrentEvent') }}
        </button>
        <button
          :disabled="loading"
          :loading="loading"
          class="ignore-vuetify-classes btn-primary ml-2 mb-1"
          @click="deleteOccurrenceEvent">
          {{ $t('agenda.button.deleteOccurrenceEvent') }}
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
    deleteRecurrentEvent(eventObject) {
      eventObject.preventDefault();
      eventObject.stopPropagation();

      this.loading = true;
      this.$eventService.deleteEvent(this.event.parent.id)
        .then(() => {
          this.$root.$emit('agenda-event-deleted', this.event.parent);
          this.dialog = false;
        })
        .finally(() => this.loading = false);
    },
    deleteOccurrenceEvent(eventObject) {
      eventObject.preventDefault();
      eventObject.stopPropagation();

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
      saveEventMethod(eventToDelete)
        .then(event => {
          this.$root.$emit('agenda-event-deleted', event);
          this.dialog = false;
        })
        .finally(() => this.loading = false);
    },
    close(event) {
      event.preventDefault();
      event.stopPropagation();
      this.dialog = false;
    },
    open() {
      this.dialog = true;
    },
  },
};
</script>