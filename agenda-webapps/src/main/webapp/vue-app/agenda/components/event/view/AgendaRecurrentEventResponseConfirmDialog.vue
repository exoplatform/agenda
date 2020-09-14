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
          @click="confirmRecurrentEvent">
          {{ $t('agenda.button.saveRecurrentEvent') }}
        </button>
        <button
          :disabled="loading"
          :loading="loading"
          class="ignore-vuetify-classes btn-primary ml-2"
          @click="confirmOccurrenceEvent">
          {{ $t('agenda.button.saveOccurrenceEvent') }}
        </button>
        <v-spacer />
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
    eventResponse: null,
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
    confirmRecurrentEvent(eventObject) {
      eventObject.preventDefault();
      eventObject.stopPropagation();

      this.loading = true;
      this.$eventService.sendEventResponse(this.event.parent.id, null, this.eventResponse)
        .then((event) => {
          this.$root.$emit('agenda-event-response-sent', event, null, this.eventResponse);
          this.dialog = false;
        })
        .finally(() => this.loading = false);
    },
    confirmOccurrenceEvent(eventObject) {
      eventObject.preventDefault();
      eventObject.stopPropagation();

      this.loading = true;
      this.$eventService.sendEventResponse(this.event.parent.id, this.event.occurrence.id, this.eventResponse)
        .then(event => {
          this.$root.$emit('agenda-event-response-sent', event, this.event.occurrence.id, this.eventResponse);
          this.dialog = false;
        })
        .finally(() => this.loading = false);
    },
    close(event) {
      event.preventDefault();
      event.stopPropagation();
      this.dialog = false;
    },
    open(eventResponse) {
      this.eventResponse = eventResponse;
      this.dialog = true;
    },
  },
};
</script>