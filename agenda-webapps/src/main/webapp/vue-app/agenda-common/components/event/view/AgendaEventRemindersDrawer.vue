<!--
SPDX-FileCopyrightText: 2021 eXo Platform SAS

SPDX-License-Identifier: AGPL-3.0-only
-->

<template>
  <exo-drawer
    ref="drawer"
    :confirm-close="confirmClose"
    :confirm-close-labels="confirmCloseLabels"
    body-classes="hide-scroll decrease-z-index-more"
    right
    persistent>
    <template slot="title">
      {{ $t('agenda.title.eventReminders') }}
    </template>
    <template slot="content">
      <v-form
        ref="agendaSettingsForm"
        class="flex"
        flat>
        <v-layout class="ma-5 d-flex flex-column">
          <agenda-reminder-user-settings ref="reminders" :reminders="eventReminders" />
        </v-layout>
      </v-form>
      <agenda-recurrent-event-reminders-confirm-dialog
        v-if="event.occurrence && event.occurrence.id"
        ref="remindersConfirmDialog"
        :event="event" />
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn me-2"
          @click="close">
          <template>
            {{ $t('agenda.button.cancel') }}
          </template>
        </v-btn>
        <v-btn
          class="btn btn-primary"
          @click="save">
          {{ $t('agenda.button.save') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: null,
    },
  },
  data: ()=> ({
    eventReminders: [],
    saved: false,
  }),
  computed: {
    confirmCloseLabels() {
      return {
        title: this.$t('agenda.settings.drawer.confirmCancelChanges.title'),
        message: this.$t('agenda.settings.drawer.confirmCancelChanges'),
        ok: this.$t('agenda.button.yes'),
        cancel: this.$t('agenda.button.no'),
      };
    },
    confirmClose() {
      return !this.saved && !this.$agendaUtils.areSameObjects(this.event.reminders, this.eventReminders);
    },
  },
  created() {
    this.$root.$on('agenda-event-reminders-saved', () => {
      this.$nextTick().then(() => this.$refs.drawer && this.$refs.drawer.close());
    });
  },
  methods: {
    open() {
      this.eventReminders = this.event.reminders && JSON.parse(JSON.stringify(this.event.reminders)) || [];

      this.saved = false;
      this.$refs.drawer.open();
    },
    close() {
      this.$refs.drawer.close();
    },
    save() {
      if (this.validateForm()) {
        if (this.event.occurrence && this.event.occurrence.id) {
          this.$refs.remindersConfirmDialog.open(this.eventReminders);
        } else {
          this.$refs.drawer.startLoading();
          return this.$eventService.saveEventReminders(this.event.id, null, this.eventReminders)
            .then(() => {
              this.saved = true;
              return this.$nextTick();
            })
            .then(() => {
              this.$root.$emit('agenda-event-reminders-saved', this.event, null, this.eventReminders);
              this.$refs.drawer.close();
            })
            .finally(() => {
              this.$refs.drawer.endLoading();
            });
        }
      }
    },
    validateForm() {
      if (!this.$refs.agendaSettingsForm.validate() // Vuetify rules
          || !this.$refs.agendaSettingsForm.$el.reportValidity()) { // Standard HTML rules
        return;
      }
      return true;
    },
  },
};
</script>