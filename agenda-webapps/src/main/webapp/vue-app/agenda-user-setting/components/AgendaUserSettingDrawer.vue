<template>
  <exo-drawer
    ref="UserSettingAgendaDrawer"
    class="UserSettingAgendaDrawer"
    body-classes="hide-scroll decrease-z-index-more"
    right>
    <template slot="title">
      {{ $t('agenda.settings.drawer.title') }}
    </template>
    <template slot="content">
      <v-form
        ref="agendaSettingsForm"
        class="flex"
        flat>
        <v-layout class="ma-5 d-flex flex-column">
          <div class="d-flex flex-column mb-5">
            <label class="switch-label-text mt-1 text-subtitle-1">{{ $t('agenda.settings.drawer.label.DefaultView') }}:</label>
            <select v-model="settingsForm.agendaDefaultView" class="width-auto my-auto pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
              <option value="day">{{ $t('agenda.label.viewDay') }}</option>
              <option value="week">{{ $t('agenda.label.viewWeek') }}</option>
              <option value="month">{{ $t('agenda.label.viewMonth') }}</option>
            </select>
          </div>
          <div class="d-flex flex-column mb-5">
            <label class="switch-label-text mt-1 text-subtitle-1">{{ $t('agenda.settings.drawer.label.WeekStartOn') }}:</label>
            <select v-model="settingsForm.agendaWeekStartOn" class="width-auto my-auto pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
              <option
                v-for="day in DAYS_ABBREVIATIONS"
                :key="day"
                :value="day">
                {{ DAY_NAME_BY_ABBREVIATION[day] }}
              </option>
            </select>
          </div>

          <div class="d-flex flex-row mt-5">
            <label class="switch-label-text mt-1 text-subtitle-1">{{ $t('agenda.settings.drawer.label.showWorkingTime') }}:</label>
            <v-switch v-model="settingsForm.showWorkingTime" class="mt-0 ml-4" />
          </div>
          <div v-if="settingsForm.showWorkingTime" class="workingTime d-flex flex-row align-center">
            <time-picker
              v-model="settingsForm.workingTimeStart"
              interval-minutes="60" />
            <label class="switch-label-text mx-5 text-subtitle-1">{{ $t('agenda.label.to') }}</label>
            <time-picker
              v-model="settingsForm.workingTimeEnd"
              interval-minutes="60" />
          </div>
        </v-layout>
      </v-form>
      <exo-confirm-dialog
        ref="CancelSavingChangesDialog"
        :message="$t('agenda.settings.drawer.confirmCancelChanges')"
        :title="$t('agenda.settings.drawer.confirmCancelChanges.title')"
        :ok-label="$t('agenda.button.confirm')"
        :cancel-label="$t('agenda.button.cancel')"
        @ok="confirmCancelSavingChanges()" />
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn mr-2"
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
import * as calendarService from '../../../vue-app/agenda/js/CalendarService.js';
import * as agendaUtils from '../../../vue-app/agenda/js/AgendaUtils.js';
export default {
  props: {
    settings: {
      type: Object,
      default: () => ({}),
    },
  },
  data() {
    return {
      DAYS_ABBREVIATIONS: ['SU', 'MO','TU','WE','TH','FR', 'SA'],
      settingsForm: {},
    };
  },
  computed: {
    DAY_NAME_BY_ABBREVIATION () {
      return {
        'MO': this.getDayFromAbbreviation('MO'),
        'TU': this.getDayFromAbbreviation('TU'),
        'WE': this.getDayFromAbbreviation('WE'),
        'TH': this.getDayFromAbbreviation('TH'),
        'FR': this.getDayFromAbbreviation('FR'),
        'SA': this.getDayFromAbbreviation('SA'),
        'SU': this.getDayFromAbbreviation('SU'),
      };
    }
  },
  created() {
    document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
    this.$root.$on('user-settings-agenda-drawer-open', this.open);
  },
  methods: {
    open() {
      const settingsForm = JSON.parse(JSON.stringify(this.settings));
      if (!settingsForm.workingTimeStart) {
        settingsForm.workingTimeStart = '08:00';
      }
      if (!settingsForm.workingTimeEnd) {
        settingsForm.workingTimeEnd = '18:00';
      }
      this.settingsForm = settingsForm;
      this.$refs.UserSettingAgendaDrawer.open();
    },
    close() {
      if(this.$agendaUtils.compareObjects(this.settingsForm, this.settings)) {
        this.$refs.UserSettingAgendaDrawer.close();
      } else {
        this.$refs.CancelSavingChangesDialog.open();
      }
    },
    save() {
      if(this.validateForm()) {
        this.$refs.UserSettingAgendaDrawer.startLoading();
        if (!this.settingsForm.showWorkingTime) {
          delete this.settingsForm.workingTimeStart;
          delete this.settingsForm.workingTimeEnd;
        }
        calendarService.saveAgendaSettings(this.settingsForm).then(() => {
          Object.assign(this.settings, this.settingsForm);
          this.$refs.UserSettingAgendaDrawer.close();
        })
          .finally(() => {
            this.$refs.UserSettingAgendaDrawer.endLoading();
          });
      }
    },
    getDayFromAbbreviation(day) {
      return agendaUtils.getDayNameFromDayAbbreviation(day, eXo.env.portal.language);
    },
    validateForm() {
      if (!this.$refs.agendaSettingsForm.validate() // Vuetify rules
          || !this.$refs.agendaSettingsForm.$el.reportValidity()) { // Standard HTML rules
        return;
      }
      return true;
    },
    confirmCancelSavingChanges() {
      this.$refs.UserSettingAgendaDrawer.close();
    }
  },
};
</script>

