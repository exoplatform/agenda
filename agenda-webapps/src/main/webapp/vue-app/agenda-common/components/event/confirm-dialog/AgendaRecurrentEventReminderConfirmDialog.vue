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
  props: {
    event: {
      type: Object,
      default: function() {
        return null;
      },
    },
  },
  data: () => ({
    recurrenceModificationType: 'single',
    loading: false,
    dialog: false,
    reminders: null,
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
        this.confirmOccurrenceEvent();
      } else if (this.recurrenceModificationType === 'all') {
        this.confirmRecurrentEvent();
      } else if (this.recurrenceModificationType === 'upcoming') {
        this.confirmUpcomingEvents();
      }
    },
    confirmUpcomingEvents(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }

      this.loading = true;
      this.$eventService.saveEventReminders(this.event.parent.id, this.event.occurrence.id, this.reminders, true)
        .then(() => {
          this.$root.$emit('agenda-event-reminders-saved', this.event, null, this.reminders);
          this.dialog = false;
        })
        .finally(() => this.loading = false);
    },
    confirmRecurrentEvent(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }

      this.loading = true;
      this.$eventService.saveEventReminders(this.event.parent.id, null, this.reminders)
        .then(() => {
          this.$root.$emit('agenda-event-reminders-saved', this.event, null, this.reminders);
          this.dialog = false;
        })
        .finally(() => this.loading = false);
    },
    confirmOccurrenceEvent(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }

      this.loading = true;
      this.$eventService.saveEventReminders(this.event.parent.id, this.event.occurrence.id, this.reminders)
        .then(() => {
          this.$root.$emit('agenda-event-reminders-saved', this.event, this.event.occurrence.id, this.reminders);
          this.dialog = false;
        })
        .finally(() => this.loading = false);
    },
    close(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }

      this.dialog = false;
    },
    open(reminders) {
      this.reminders = reminders;
      this.dialog = true;
    },
  },
};
</script>