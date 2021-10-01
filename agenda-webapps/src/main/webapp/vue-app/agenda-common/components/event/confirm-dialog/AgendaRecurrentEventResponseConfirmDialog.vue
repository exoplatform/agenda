<!--
SPDX-FileCopyrightText: 2021 eXo Platform SAS

SPDX-License-Identifier: AGPL-3.0-only
-->

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
        <v-radio-group v-model="recurrenceResponseType">
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
          @click="sendRecurrentEventChoice">
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
    recurrenceResponseType: 'single',
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
    sendRecurrentEventChoice(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }
      if (this.recurrenceResponseType === 'single') {
        this.confirmOccurrenceEvent();
      } else if (this.recurrenceResponseType === 'all') {
        this.confirmRecurrentEvent();
      } else if (this.recurrenceResponseType === 'upcoming') {
        this.confirmUpcomingEvent();
      }
    },
    confirmUpcomingEvent() {
      this.loading = true;
      this.$eventService.sendEventResponse(this.event.parent.id, this.event.occurrence.id, this.eventResponse, true)
        .then(() => {
          this.$root.$emit('agenda-event-response-sent', this.event.parent, null, this.eventResponse);
          this.dialog = false;
        })
        .finally(() => this.loading = false);
    },
    confirmRecurrentEvent() {
      this.loading = true;
      this.$eventService.sendEventResponse(this.event.parent.id, null, this.eventResponse)
        .then(() => {
          this.$root.$emit('agenda-event-response-sent', this.event.parent, null, this.eventResponse);
          this.dialog = false;
        })
        .finally(() => this.loading = false);
    },
    confirmOccurrenceEvent() {
      this.loading = true;
      this.$eventService.sendEventResponse(this.event.parent.id, this.event.occurrence.id, this.eventResponse)
        .then(() => {
          this.$root.$emit('agenda-event-response-sent', this.event, this.event.occurrence.id, this.eventResponse);
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