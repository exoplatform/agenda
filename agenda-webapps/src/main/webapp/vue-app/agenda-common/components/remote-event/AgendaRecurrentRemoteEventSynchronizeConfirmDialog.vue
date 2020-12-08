<template>
  <v-dialog
    v-model="dialog"
    content-class="uiPopup width-auto"
    max-width="100vw">
    <v-card class="elevation-12">
      <div class="ignore-vuetify-classes popupHeader ClearFix">
        <a
          class="uiIconClose pull-right"
          aria-hidden="true"
          @click="close"></a>
        <span class="ignore-vuetify-classes PopupTitle popupTitle text-truncate">
          {{ $t('agenda.title.confirmsynchronizeRecurrentEvent') }}
        </span>
      </div>
      <v-card-text>
        {{ $t('agenda.message.confirmsynchronizeRecurrentEvent') }}
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
          @click="synchronizeRecurrentEvent">
          {{ $t('agenda.button.synchronizeRecurrentEvent') }}
        </button>
        <button
          :disabled="loading"
          :loading="loading"
          class="ignore-vuetify-classes btn-primary ml-2 mb-1"
          @click="synchronizeOccurrenceEvent">
          {{ $t('agenda.button.synchronizeOccurrenceEvent') }}
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
    connectedConnector: {
      type: Object,
      default: () => null
    },
  },
  data: () => ({
    loading: false,
    dialog: false,
    allRecurrentEvent: false,
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
    synchronizeRecurrentEvent(eventObject) {
      eventObject.preventDefault();
      eventObject.stopPropagation();

      const eventToSynchronize =  this.event.parent;
      this.allRecurrentEvent = true;
      this.$root.$emit('agenda-connector-synchronize-event', this.connectedConnector, eventToSynchronize, this.allRecurrentEvent);
      this.dialog = false;
    },
    synchronizeOccurrenceEvent(eventObject) {
      eventObject.preventDefault();
      eventObject.stopPropagation();

      const eventToSynchronize = Object. assign({}, this.event);
      this.allRecurrentEvent = false;
      delete eventToSynchronize.parent;
      this.$root.$emit('agenda-connector-synchronize-event', this.connectedConnector, eventToSynchronize, this.allRecurrentEvent);
      this.dialog = false;
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